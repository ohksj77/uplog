"use client";

import { useEffect, useId, useLayoutEffect, useRef, useState, type ClipboardEvent, type DragEvent, type FormEvent } from "react";
import { ConnectErrorPanel } from "@/components/connect/ConnectErrorPanel";
import { connectErrorView, type KeyFormMode } from "@/components/connect/connect-errors";
import { looksLikeClientId, looksLikeClientSecret, normalizeCredential, parseCredentials } from "@/components/connect/parse-credentials";
import { fieldError, input, primaryButton, secondaryButton } from "@/components/connect/styles";
import type { ApiError } from "@/lib/api/client";
import { AccountApiError, type CredentialField, type TossCredentials } from "@/lib/api/account-types";

type Props = {
  busy: boolean;
  error: ApiError | null;
  fallbackIps: string[];
  mode?: KeyFormMode;
  onBack: () => void;
  backLabel?: string;
  /** 로그인이 풀렸을 때 돌아올 경로 */
  loginNext?: string;
  /** 키를 넘긴다. 성공하면 null, 실패하면 그 오류(부모가 error prop으로도 내려 준다). */
  onSubmit: (credentials: TossCredentials) => Promise<ApiError | null>;
};

/** IP 오류 뒤 "다시 확인"에 쓸 키를 기억하는 시간 */
const RECHECK_KEEP_MS = 10 * 60 * 1000;

/**
 * client_id·client_secret 입력.
 *
 * 비밀값을 React 상태·저장소에 두지 않는 방법:
 * - client_secret 칸은 비제어 입력이라 값은 DOM에만 있고, 제출할 때 ref로 읽는다.
 * - "한 번에 붙여 넣기" 칸은 붙여 넣기 이벤트를 막고 클립보드 글을 바로 나눠, secret은 ref로 secret 칸에 직접 쓴다.
 *   다른 방법(직접 입력·끌어 놓기)으로 들어온 글도 나눈 즉시 칸을 비운다.
 * - IP 오류 뒤 다시 확인용 키는 ref에만 두고, 성공·다른 오류·화면 이탈·10분 경과 때 지운다.
 * - 실패하거나 화면을 떠나면 secret 칸을 비운다.
 */
export function KeyForm({ busy, error, fallbackIps, mode = "connect", onBack, backLabel = "이전", loginNext, onSubmit }: Props) {
  const id = useId();
  const idRef = useRef<HTMLInputElement>(null);
  const secretRef = useRef<HTMLInputElement>(null);
  const pasteRef = useRef<HTMLTextAreaElement>(null);
  const submitRef = useRef<HTMLButtonElement>(null);
  const errorRef = useRef<HTMLDivElement>(null);
  const [clientId, setClientId] = useState("");
  const [showSecret, setShowSecret] = useState(false);
  const [missing, setMissing] = useState<{ id: boolean; secret: boolean }>({ id: false, secret: false });
  // 붙여 넣기 결과 안내(값은 담지 않는다). aria-live로 읽힌다.
  const [pasteNote, setPasteNote] = useState<string | null>(null);
  // secret 칸 형식 안내. 값 대신 "tssk_로 시작하는가"만 둔다.
  const [secretFormatOdd, setSecretFormatOdd] = useState(false);
  // 사용자가 틀린 칸을 고치기 시작하면 그 칸의 서버 오류 문구를 내린다.
  const [editedAfter, setEditedAfter] = useState<ApiError | null>(null);
  // IP 오류 뒤 다시 확인할 키. 상태가 아닌 ref에만 둔다.
  const recheckRef = useRef<{ keys: TossCredentials; timer: ReturnType<typeof setTimeout> } | null>(null);
  const [canRecheck, setCanRecheck] = useState(false);

  const ids = {
    paste: `${id}-paste`,
    pasteHint: `${id}-paste-hint`,
    clientId: `${id}-client-id`,
    secret: `${id}-secret`,
    idHint: `${id}-id-hint`,
    idFormat: `${id}-id-format`,
    idError: `${id}-id-error`,
    secretHint: `${id}-secret-hint`,
    secretFormat: `${id}-secret-format`,
    secretError: `${id}-secret-error`,
    error: `${id}-error`,
  };

  // 토스가 틀린 칸을 알려 준 키 오류면 그 칸에만 묶고, 아니면 두 칸 모두에 묶는다(패널).
  const field: CredentialField | null = error instanceof AccountApiError && error.code === "TOSS_INVALID_CREDENTIALS" ? error.field : null;
  const fieldMessage = field && editedAfter !== error && error ? error.message : null;
  const bothInvalid = error != null && field == null && connectErrorView(error, mode).credentialField === true;
  const showPanel = error != null && field == null;

  function clearRecheck() {
    if (recheckRef.current) clearTimeout(recheckRef.current.timer);
    recheckRef.current = null;
    setCanRecheck(false);
  }

  function keepForRecheck(keys: TossCredentials) {
    if (recheckRef.current) clearTimeout(recheckRef.current.timer);
    const timer = setTimeout(() => {
      recheckRef.current = null;
      setCanRecheck(false);
    }, RECHECK_KEEP_MS);
    recheckRef.current = { keys, timer };
    setCanRecheck(true);
  }

  // 화면이 숨겨지면(Activity) 비밀값, 붙여 넣기 칸, 다시 확인용 키를 지운다.
  useLayoutEffect(() => {
    const secret = secretRef.current;
    const paste = pasteRef.current;
    return () => {
      if (secret) secret.value = "";
      if (paste) paste.value = "";
      if (recheckRef.current) clearTimeout(recheckRef.current.timer);
      recheckRef.current = null;
      setCanRecheck(false);
      setShowSecret(false);
      setSecretFormatOdd(false);
    };
  }, []);

  // 새 오류가 오면: 틀린 칸이 정해졌으면 그 칸으로, 아니면 안내 상자로 초점을 옮긴다.
  useEffect(() => {
    if (!error) return;
    if (field === "clientId") idRef.current?.focus();
    else if (field === "clientSecret") secretRef.current?.focus();
    else errorRef.current?.focus();
  }, [error, field]);

  /** 붙여 넣은 글을 나눠 두 칸을 채운다. 글은 어디에도 남기지 않는다. */
  function fillFrom(text: string) {
    const parsed = parseCredentials(text);
    if (pasteRef.current) pasteRef.current.value = "";
    if (!text.trim()) return;

    if (parsed.clientId) setClientId(parsed.clientId);
    if (parsed.clientSecret && secretRef.current) {
      secretRef.current.value = parsed.clientSecret;
      setSecretFormatOdd(!looksLikeClientSecret(parsed.clientSecret));
    }
    setMissing({ id: false, secret: false });

    const secretFilled = parsed.clientSecret != null || Boolean(secretRef.current?.value);
    const idFilled = parsed.clientId != null || clientId.trim() !== "";
    if (parsed.clientId && parsed.clientSecret) {
      setPasteNote("client_id와 client_secret을 채웠어요. 확인 버튼을 눌러 주세요.");
      submitRef.current?.focus();
    } else if (parsed.clientId || parsed.clientSecret) {
      const which = parsed.clientId ? "client_id" : "client_secret";
      const other = parsed.clientId ? "client_secret" : "client_id";
      const masked = parsed.maskedSecret ? " client_secret은 가려져 있어 쓸 수 없어요. 발급할 때 복사해 둔 값을 넣거나, 없으면 재발급해 주세요." : "";
      setPasteNote(`${which}를 채웠어요.${idFilled && secretFilled ? "" : ` ${other}는 아래 칸에 따로 붙여 넣어 주세요.`}${masked}`);
      if (!idFilled) idRef.current?.focus();
      else if (!secretFilled) secretRef.current?.focus();
      else submitRef.current?.focus();
    } else if (parsed.maskedSecret) {
      setPasteNote("client_secret이 가려진 값(tssk_live_****)이라 쓸 수 없어요. 발급할 때 복사해 둔 값을 넣거나, 없으면 재발급해 주세요.");
    } else {
      setPasteNote("키를 찾지 못했어요. client_id(tsck_로 시작)와 client_secret(tssk_로 시작)을 아래 칸에 하나씩 붙여 넣어 주세요.");
    }
  }

  function onPaste(e: ClipboardEvent<HTMLTextAreaElement>) {
    // 칸에 글이 들어가기 전에 가로채서, 비밀값이 화면·DOM에 머물지 않게 한다.
    e.preventDefault();
    fillFrom(e.clipboardData.getData("text"));
  }

  function onDrop(e: DragEvent<HTMLTextAreaElement>) {
    const text = e.dataTransfer.getData("text");
    if (!text) return;
    e.preventDefault();
    fillFrom(text);
  }

  async function send(credentials: TossCredentials) {
    setPasteNote(null);
    const result = await onSubmit(credentials);
    const secretInput = secretRef.current;
    if (!result) {
      clearRecheck();
      return;
    }
    if (result.code === "TOSS_IP_NOT_ALLOWED") {
      // 키는 맞을 수 있으니 이 흐름의 메모리(ref)에 두고 "다시 확인"으로 그대로 보낸다. 칸은 비운다.
      keepForRecheck(credentials);
      if (secretInput) secretInput.value = "";
      setShowSecret(false);
      return;
    }
    clearRecheck();
    const badField = result instanceof AccountApiError && result.code === "TOSS_INVALID_CREDENTIALS" ? result.field : null;
    if (badField === "clientId") {
      // client_id만 틀렸으면 그 칸만 비우고, secret은 다시 붙여 넣지 않아도 되게 둔다.
      setClientId("");
      return;
    }
    if (secretInput) secretInput.value = "";
    setShowSecret(false);
    setSecretFormatOdd(false);
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    const secretInput = secretRef.current;
    const credentials = { clientId: normalizeCredential(clientId), clientSecret: normalizeCredential(secretInput?.value ?? "") };
    const nextMissing = { id: !credentials.clientId, secret: !credentials.clientSecret };
    setMissing(nextMissing);
    if (nextMissing.id) return idRef.current?.focus();
    if (nextMissing.secret) return secretInput?.focus();
    if (credentials.clientId !== clientId) setClientId(credentials.clientId);
    await send(credentials);
  }

  function recheck() {
    const kept = recheckRef.current;
    if (!kept) {
      setCanRecheck(false);
      return;
    }
    void send(kept.keys);
  }

  const describe = (...parts: (string | false | null)[]) => parts.filter(Boolean).join(" ") || undefined;
  const idTrimmed = clientId.trim();
  const idFormatOdd = idTrimmed !== "" && !looksLikeClientId(idTrimmed);
  const idInvalid = missing.id || bothInvalid || field === "clientId";
  const secretInvalid = missing.secret || bothInvalid || field === "clientSecret";

  return (
    <form onSubmit={submit} noValidate autoComplete="off" className="space-y-5">
      <div>
        <label htmlFor={ids.paste} className="block text-sm font-medium">
          한 번에 붙여 넣기
        </label>
        <p id={ids.pasteHint} className="mt-1 text-xs text-muted break-keep">
          client_id와 client_secret을 함께 복사했다면 여기에 붙여 넣으세요. 아래 두 칸을 알아서 채우고, 이 칸은 바로 비워요.
        </p>
        <textarea
          ref={pasteRef}
          id={ids.paste}
          rows={2}
          defaultValue=""
          onPaste={onPaste}
          onDrop={onDrop}
          onBlur={(e) => {
            // 붙여 넣기 이벤트 없이 들어온 글(직접 입력 등)도 칸을 떠날 때 나누고 비운다.
            if (e.currentTarget.value) fillFrom(e.currentTarget.value);
          }}
          autoComplete="off"
          autoCapitalize="off"
          autoCorrect="off"
          spellCheck={false}
          data-1p-ignore
          data-lpignore="true"
          aria-describedby={ids.pasteHint}
          placeholder="client_id: tsck_live_…  client_secret: tssk_live_…"
          className={`${input} mt-2 h-auto min-h-16 resize-none py-3 font-mono text-sm`}
        />
        <p aria-live="polite" className={pasteNote ? "mt-2 text-sm break-keep" : "sr-only"}>
          {pasteNote ?? ""}
        </p>
      </div>

      <div>
        <label htmlFor={ids.clientId} className="block text-sm font-medium">
          client_id
        </label>
        <p id={ids.idHint} className="mt-1 text-xs text-muted break-keep">
          Open API Key 관리의 Client Id예요. 옆의 복사 버튼으로 복사하면 정확해요.
        </p>
        <input
          ref={idRef}
          id={ids.clientId}
          name="clientId"
          value={clientId}
          onChange={(e) => {
            setClientId(e.target.value);
            if (field === "clientId") setEditedAfter(error);
          }}
          onBlur={() => {
            const cleaned = normalizeCredential(clientId);
            if (cleaned !== clientId) setClientId(cleaned);
          }}
          autoComplete="off"
          autoCapitalize="off"
          autoCorrect="off"
          spellCheck={false}
          placeholder="tsck_live_…"
          aria-invalid={idInvalid ? true : undefined}
          aria-describedby={describe(
            ids.idHint,
            idFormatOdd && ids.idFormat,
            missing.id && ids.idError,
            field === "clientId" && fieldMessage && ids.idError,
            bothInvalid && ids.error,
          )}
          className={`${input} mt-2 font-mono`}
        />
        {idFormatOdd && (
          <p id={ids.idFormat} className="mt-2 text-xs text-muted break-keep">
            토스증권 client_id는 보통 tsck_live_로 시작해요. 일부만 복사되지 않았는지 확인해 주세요.
          </p>
        )}
        {(missing.id || (field === "clientId" && fieldMessage)) && (
          <p id={ids.idError} className={`${fieldError} break-keep`}>
            {missing.id ? "client_id를 입력해 주세요." : fieldMessage}
          </p>
        )}
      </div>

      <div>
        <label htmlFor={ids.secret} className="block text-sm font-medium">
          client_secret
        </label>
        <p id={ids.secretHint} className="mt-1 text-xs text-muted break-keep">
          키를 발급할 때 한 번만 보이는 값이에요. 이 브라우저에는 저장하지 않고, 확인에만 쓰고 바로 지워요.
        </p>
        <div className="mt-2 flex gap-2">
          <input
            ref={secretRef}
            id={ids.secret}
            name="clientSecret"
            type={showSecret ? "text" : "password"}
            defaultValue=""
            onChange={(e) => {
              // 값은 두지 않고 "tssk_로 시작하는가"만 본다.
              const v = e.currentTarget.value.trim();
              setSecretFormatOdd(v !== "" && !looksLikeClientSecret(v));
              if (field === "clientSecret") setEditedAfter(error);
            }}
            autoComplete="off"
            autoCapitalize="off"
            autoCorrect="off"
            spellCheck={false}
            placeholder="tssk_live_…"
            data-1p-ignore
            data-lpignore="true"
            aria-invalid={secretInvalid ? true : undefined}
            aria-describedby={describe(
              ids.secretHint,
              secretFormatOdd && ids.secretFormat,
              missing.secret && ids.secretError,
              field === "clientSecret" && fieldMessage && ids.secretError,
              bothInvalid && ids.error,
            )}
            className={`${input} font-mono`}
          />
          <button
            type="button"
            onClick={() => setShowSecret((v) => !v)}
            aria-pressed={showSecret}
            aria-controls={ids.secret}
            className={`${secondaryButton} shrink-0 px-4`}
          >
            {showSecret ? "숨기기" : "보기"}
          </button>
        </div>
        {secretFormatOdd && (
          <p id={ids.secretFormat} className="mt-2 text-xs text-muted break-keep">
            토스증권 client_secret은 보통 tssk_live_로 시작해요. client_id를 넣었거나 일부만 복사되지 않았는지 확인해 주세요.
          </p>
        )}
        {(missing.secret || (field === "clientSecret" && fieldMessage)) && (
          <p id={ids.secretError} className={`${fieldError} break-keep`}>
            {missing.secret ? "client_secret을 입력해 주세요." : fieldMessage}
          </p>
        )}
      </div>

      {showPanel && error && (
        <ConnectErrorPanel
          ref={errorRef}
          id={ids.error}
          error={error}
          fallbackIps={fallbackIps}
          mode={mode}
          loginNext={loginNext}
          busy={busy}
          onRecheck={canRecheck && error.code === "TOSS_IP_NOT_ALLOWED" ? recheck : undefined}
        />
      )}

      <p aria-live="polite" className="min-h-5 text-sm text-muted">
        {busy ? (mode === "rekey" ? "토스증권에 새 키를 확인하고 있어요. 몇 초 걸릴 수 있어요." : "토스증권에 키와 계좌를 확인하고 있어요. 몇 초 걸릴 수 있어요.") : ""}
      </p>

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-between">
        <button type="button" onClick={onBack} className={secondaryButton}>
          {backLabel}
        </button>
        <button ref={submitRef} type="submit" disabled={busy} aria-disabled={busy} className={canRecheck ? secondaryButton : primaryButton}>
          {busy ? "확인하는 중…" : mode === "rekey" ? "새 키로 바꾸기" : "연결 확인"}
        </button>
      </div>
    </form>
  );
}
