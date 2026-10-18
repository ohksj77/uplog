"use client";

import { useEffect, useId, useLayoutEffect, useRef, useState, type FormEvent } from "react";
import { ConnectErrorPanel } from "@/components/connect/ConnectErrorPanel";
import { connectErrorView } from "@/components/connect/connect-errors";
import { fieldError, input, primaryButton, secondaryButton } from "@/components/connect/styles";
import type { ApiError } from "@/lib/api/client";
import type { TossCredentials } from "@/lib/api/account-types";

type Props = {
  busy: boolean;
  error: ApiError | null;
  fallbackIps: string[];
  onBack: () => void;
  /** 키를 넘긴다. 실패하면 false — client_secret 칸을 비운다. */
  onSubmit: (credentials: TossCredentials) => Promise<boolean>;
};

/**
 * client_id·client_secret 입력. client_secret은 React 상태에 두지 않고(비제어 입력) 제출할 때만 읽는다.
 * 실패하거나 화면을 떠나면 칸을 비운다.
 */
export function KeyForm({ busy, error, fallbackIps, onBack, onSubmit }: Props) {
  const id = useId();
  const idRef = useRef<HTMLInputElement>(null);
  const secretRef = useRef<HTMLInputElement>(null);
  const errorRef = useRef<HTMLDivElement>(null);
  const [clientId, setClientId] = useState("");
  const [showSecret, setShowSecret] = useState(false);
  const [missing, setMissing] = useState<{ id: boolean; secret: boolean }>({ id: false, secret: false });

  const ids = {
    clientId: `${id}-client-id`,
    secret: `${id}-secret`,
    idHint: `${id}-id-hint`,
    idError: `${id}-id-error`,
    secretHint: `${id}-secret-hint`,
    secretError: `${id}-secret-error`,
    error: `${id}-error`,
  };
  const credentialError = error != null && connectErrorView(error).credentialField === true;

  // 화면이 숨겨지면(Activity) 비밀값과 보기 상태를 지운다.
  useLayoutEffect(() => {
    const secret = secretRef.current;
    return () => {
      if (secret) secret.value = "";
      setShowSecret(false);
    };
  }, []);

  // 새 오류가 오면 안내 상자로 초점을 옮긴다.
  useEffect(() => {
    if (error) errorRef.current?.focus();
  }, [error]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    const secretInput = secretRef.current;
    const credentials = { clientId: clientId.trim(), clientSecret: secretInput?.value.trim() ?? "" };
    const nextMissing = { id: !credentials.clientId, secret: !credentials.clientSecret };
    setMissing(nextMissing);
    if (nextMissing.id) return idRef.current?.focus();
    if (nextMissing.secret) return secretInput?.focus();

    const ok = await onSubmit(credentials);
    if (!ok && secretInput) {
      secretInput.value = "";
      setShowSecret(false);
    }
  }

  const describe = (...parts: (string | false)[]) => parts.filter(Boolean).join(" ") || undefined;

  return (
    <form onSubmit={submit} noValidate autoComplete="off" className="space-y-5">
      <div>
        <label htmlFor={ids.clientId} className="block text-sm font-medium">
          client_id
        </label>
        <p id={ids.idHint} className="mt-1 text-xs text-muted">
          WTS 설정 &gt; Open API에서 발급한 키의 ID예요.
        </p>
        <input
          ref={idRef}
          id={ids.clientId}
          name="clientId"
          value={clientId}
          onChange={(e) => setClientId(e.target.value)}
          autoComplete="off"
          autoCapitalize="off"
          autoCorrect="off"
          spellCheck={false}
          aria-invalid={missing.id || credentialError ? true : undefined}
          aria-describedby={describe(ids.idHint, missing.id && ids.idError, credentialError && ids.error)}
          className={`${input} mt-2 font-mono`}
        />
        {missing.id && (
          <p id={ids.idError} className={fieldError}>
            client_id를 입력해 주세요.
          </p>
        )}
      </div>

      <div>
        <label htmlFor={ids.secret} className="block text-sm font-medium">
          client_secret
        </label>
        <p id={ids.secretHint} className="mt-1 text-xs text-muted">
          이 브라우저에는 저장하지 않아요. 연결 확인에만 쓰고 바로 지워요.
        </p>
        <div className="mt-2 flex gap-2">
          <input
            ref={secretRef}
            id={ids.secret}
            name="clientSecret"
            type={showSecret ? "text" : "password"}
            defaultValue=""
            autoComplete="off"
            autoCapitalize="off"
            autoCorrect="off"
            spellCheck={false}
            data-1p-ignore
            data-lpignore="true"
            aria-invalid={missing.secret || credentialError ? true : undefined}
            aria-describedby={describe(ids.secretHint, missing.secret && ids.secretError, credentialError && ids.error)}
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
        {missing.secret && (
          <p id={ids.secretError} className={fieldError}>
            client_secret을 입력해 주세요.
          </p>
        )}
      </div>

      {error && <ConnectErrorPanel ref={errorRef} id={ids.error} error={error} fallbackIps={fallbackIps} />}

      <p aria-live="polite" className="min-h-5 text-sm text-muted">
        {busy ? "토스증권에 키와 계좌를 확인하고 있어요. 몇 초 걸릴 수 있어요." : ""}
      </p>

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-between">
        <button type="button" onClick={onBack} className={secondaryButton}>
          이전
        </button>
        <button type="submit" disabled={busy} aria-disabled={busy} className={primaryButton}>
          {busy ? "확인하는 중…" : "연결 확인"}
        </button>
      </div>
    </form>
  );
}
