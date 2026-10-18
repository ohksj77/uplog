"use client";

import Link from "next/link";
import { useEffect, useId, useLayoutEffect, useRef, useState, useSyncExternalStore } from "react";
import { CopyButton } from "@/components/connect/CopyButton";
import { card, primaryButton, secondaryButton, textLink } from "@/components/connect/styles";
import { ApiError } from "@/lib/api/client";
import { createHandoff } from "@/lib/api/account-client";
import { refreshMe } from "@/lib/use-me";

// 휴대폰에서 연결을 시작한 사용자를 PC로 넘기는 카드. 토스증권 키 발급·IP 등록 화면은 PC 웹에서만 열리기 때문이다.
// 1회용 코드(10분)로 만든 /continue?token=… 링크를 QR·복사·공유로 건네고, PC에서 연결을 마치면 3초마다 묻는 /api/me로 알아챈다.

const HANDHELD_QUERY = "(pointer: coarse), (max-width: 767px)";
const POLL_MS = 3000;

function subscribeHandheld(onChange: () => void) {
  const mq = window.matchMedia(HANDHELD_QUERY);
  mq.addEventListener("change", onChange);
  return () => mq.removeEventListener("change", onChange);
}

/** 휴대폰·태블릿(터치 위주이거나 좁은 화면)인가. 서버 렌더에서는 false(PC)로 본다. */
export function useHandheld(): boolean {
  return useSyncExternalStore(
    subscribeHandheld,
    () => window.matchMedia(HANDHELD_QUERY).matches,
    () => false,
  );
}

type Handoff = { url: string; expiresAt: number; qr: string | null };

type Phase =
  | { kind: "idle" }
  | { kind: "creating" }
  | { kind: "shown"; handoff: Handoff }
  | { kind: "expired" }
  | { kind: "connected" }
  | { kind: "error"; message: string };

type Props = {
  /** PC에서 연결을 마쳤는지 /api/me로 지켜볼까. 이미 연결된 계좌의 키 다시 넣기에서는 끈다. */
  watchConnection?: boolean;
  /** PC에서 로그인한 뒤 열 화면. 기본은 /connect */
  nextPath?: string;
  /** 이어 하기 링크를 보여 주는 동안(PC 연결을 기다리는 중) true. 연결 화면이 "이미 연결됨"으로 바뀌지 않게 쓴다. */
  onWaitingChange?: (waiting: boolean) => void;
};

/**
 * 휴대폰에서는 카드로 크게, PC에서는 작은 링크로 보여 준다(링크를 누르면 같은 카드가 펼쳐진다).
 */
export function ContinueOnPc({ onWaitingChange, watchConnection = true, nextPath }: Props) {
  const handheld = useHandheld();
  const [expanded, setExpanded] = useState(false);
  if (!handheld && !expanded) {
    return (
      <p className="text-sm text-muted break-keep">
        다른 기기에서 이어서 하고 싶다면{" "}
        <button type="button" onClick={() => setExpanded(true)} className={textLink}>
          이어 하기 링크 만들기
        </button>
      </p>
    );
  }
  return <ContinueCard onWaitingChange={onWaitingChange} watchConnection={watchConnection} nextPath={nextPath} />;
}

function ContinueCard({ onWaitingChange, watchConnection = true, nextPath }: Props) {
  const id = useId();
  const [phase, setPhase] = useState<Phase>({ kind: "idle" });
  const [now, setNow] = useState(() => Date.now());
  const statusRef = useRef<HTMLParagraphElement>(null);
  const waitingRef = useRef(onWaitingChange);
  useEffect(() => {
    waitingRef.current = onWaitingChange;
  });

  // 부모에게 "PC 연결을 기다리는 중"을 알린다. 연결된 뒤에도 이 카드가 결과를 보여 줘야 하므로 계속 true.
  const waiting = phase.kind === "shown" || phase.kind === "connected";
  useEffect(() => {
    waitingRef.current?.(waiting);
  }, [waiting]);

  useEffect(() => {
    if (phase.kind === "connected") statusRef.current?.focus();
  }, [phase.kind]);

  // 보여 주는 동안: 1초마다 남은 시간(지나면 만료), 3초마다 /api/me로 PC 연결 여부. 화면을 떠나면(effect 정리) 멈춘다.
  const expiresAt = phase.kind === "shown" ? phase.handoff.expiresAt : null;
  useEffect(() => {
    if (expiresAt == null) return;
    const tick = setInterval(() => {
      const t = Date.now();
      setNow(t);
      if (t >= expiresAt) setPhase((p) => (p.kind === "shown" ? { kind: "expired" } : p));
    }, 1000);
    const poll = setInterval(() => {
      if (!watchConnection) return;
      void refreshMe().then((me) => {
        const status = me?.connection?.status;
        if (status === "BACKFILLING" || status === "ACTIVE") setPhase((p) => (p.kind === "shown" ? { kind: "connected" } : p));
      });
    }, POLL_MS);
    return () => {
      clearInterval(tick);
      clearInterval(poll);
    };
  }, [expiresAt, watchConnection]);

  // 다른 화면으로 떠나면(Activity로 숨겨짐) 1회용 링크를 화면에서 지운다.
  useLayoutEffect(() => {
    return () => {
      setPhase((p) => (p.kind === "shown" ? { kind: "idle" } : p));
      waitingRef.current?.(false);
    };
  }, []);

  async function create() {
    setPhase({ kind: "creating" });
    try {
      const res = await createHandoff();
      const params = new URLSearchParams({ token: res.token });
      if (nextPath && nextPath !== "/connect") params.set("next", nextPath);
      const url = `${window.location.origin}/continue?${params}`;
      const parsed = Date.parse(res.expiresAt);
      const expiresAt = Number.isNaN(parsed) ? Date.now() + 10 * 60 * 1000 : parsed;
      const qr = await makeQr(url);
      setNow(Date.now());
      setPhase({ kind: "shown", handoff: { url, expiresAt, qr } });
    } catch (e) {
      setPhase({ kind: "error", message: e instanceof ApiError ? e.message : "이어 하기 링크를 만들지 못했어요. 잠시 후 다시 시도해 주세요." });
    }
  }

  return (
    <section aria-labelledby={`${id}-title`} className={`${card} space-y-4`}>
      <div className="space-y-1">
        <h2 id={`${id}-title`} className="font-semibold break-keep">
          PC에서 이어 하기
        </h2>
        <p className="text-sm text-muted break-keep">토스증권 웹(WTS)은 PC 웹에서 열려요. 키 발급과 IP 등록은 PC에서 하는 게 편해요. 로그인한 채로 PC에서 이어서 할 수 있는 링크를 만들어 드릴게요.</p>
      </div>

      {(phase.kind === "idle" || phase.kind === "creating" || phase.kind === "error") && (
        <>
          <button type="button" onClick={create} disabled={phase.kind === "creating"} className={`${primaryButton} w-full sm:w-auto`}>
            {phase.kind === "creating" ? "링크 만드는 중…" : "PC에서 열 링크 만들기"}
          </button>
          {phase.kind === "error" && (
            <p role="alert" className="text-sm break-keep">
              {phase.message}
            </p>
          )}
        </>
      )}

      {phase.kind === "shown" && <HandoffLink id={id} handoff={phase.handoff} now={now} watching={watchConnection} />}

      {phase.kind === "expired" && (
        <div className="space-y-3">
          <p role="status" className="text-sm break-keep">
            링크 시간이 지났어요. 필요하면 새로 만들어 주세요.
          </p>
          <button type="button" onClick={create} className={`${secondaryButton} w-full sm:w-auto`}>
            새 링크 만들기
          </button>
        </div>
      )}

      {phase.kind === "connected" && (
        <div className="space-y-3">
          <p ref={statusRef} tabIndex={-1} className="font-semibold outline-none">
            PC에서 연결했어요
          </p>
          <p className="text-sm text-muted break-keep">이제 이 기기에서도 내 계좌 로그를 볼 수 있어요. 지난 기록을 만드는 데 조금 걸릴 수 있어요.</p>
          <Link href="/" className={`${primaryButton} w-full sm:w-auto`}>
            홈으로 가기
          </Link>
        </div>
      )}

      <p aria-live="polite" className="sr-only">
        {phase.kind === "shown" ? `이어 하기 링크를 만들었어요.${watchConnection ? " PC에서 연결을 마치면 여기에 알려 드릴게요." : ""}` : ""}
        {phase.kind === "connected" ? "PC에서 연결했어요." : ""}
      </p>
    </section>
  );
}

function HandoffLink({ id, handoff, now, watching }: { id: string; handoff: Handoff; now: number; watching: boolean }) {
  const remaining = Math.max(0, Math.ceil((handoff.expiresAt - now) / 1000));
  const mm = Math.floor(remaining / 60);
  const ss = String(remaining % 60).padStart(2, "0");
  const canShare = typeof navigator !== "undefined" && typeof navigator.share === "function";

  return (
    <div className="space-y-4">
      <ol className="list-decimal space-y-1 pl-5 text-sm break-keep marker:text-muted">
        <li>PC 브라우저에 아래 주소를 입력하거나, 링크를 나에게 보내 PC에서 열어 주세요.</li>
        <li>PC에서 업로그가 이 계정으로 로그인된 채 {watching ? "연결 화면" : "키 다시 넣기 화면"}을 열어요.</li>
      </ol>
      {handoff.qr && (
        <div className="flex justify-center">
          {/* QR은 스캐너가 읽을 수 있도록 흰 바탕 검은 점(라이브러리 기본색)으로 그린다. */}
          {/* eslint-disable-next-line @next/next/no-img-element -- 브라우저에서 만든 data: SVG */}
          <img
            src={handoff.qr}
            width={176}
            height={176}
            alt="PC에서 이어 하기 링크로 가는 QR 코드. 같은 주소가 아래에 글자로 있어요."
            className="size-44 rounded-lg border border-border"
          />
        </div>
      )}
      <div className="space-y-2">
        <p id={`${id}-url-label`} className="text-xs text-muted">
          이어 하기 주소
        </p>
        <div className="flex items-start gap-2 rounded-xl border border-border bg-background px-3 py-2">
          <code aria-labelledby={`${id}-url-label`} className="min-w-0 flex-1 font-mono text-sm break-all select-all">
            {handoff.url}
          </code>
          <CopyButton value={handoff.url} label="이어 하기 주소" />
        </div>
        {canShare && (
          <button
            type="button"
            onClick={() => void navigator.share({ title: "업로그 이어 하기", url: handoff.url }).catch(() => {})}
            className={`${secondaryButton} w-full sm:w-auto`}
          >
            나에게 보내기
          </button>
        )}
      </div>
      <p className="text-xs text-muted break-keep">
        <span className="tabular-nums" aria-hidden>
          {mm}:{ss}
        </span>
        <span className="sr-only">
          {mm}분 {ss}초
        </span>{" "}
        뒤에 만료돼요. 한 번만 쓸 수 있고, 링크를 가진 사람은 내 계정으로 로그인할 수 있으니 다른 사람에게 보내지 마세요.
      </p>
      <p className="text-sm text-muted break-keep">{watching ? "PC에서 연결을 기다리고 있어요." : "PC에서 키를 넣으면 이 기기에도 바로 반영돼요."}</p>
    </div>
  );
}

/** URL을 QR SVG data URL로 만든다. 라이브러리는 이때만 불러온다. 실패하면 QR 없이 주소만 보여 준다. */
async function makeQr(url: string): Promise<string | null> {
  try {
    const QRCode = (await import("qrcode")).default;
    const svg = await QRCode.toString(url, { type: "svg", margin: 2, errorCorrectionLevel: "M" });
    return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
  } catch {
    return null;
  }
}
