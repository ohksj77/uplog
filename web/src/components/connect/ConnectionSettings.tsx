"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";
import { RequireLogin } from "@/components/auth/RequireLogin";
import { BackfillProgress, formatDateTime } from "@/components/connect/BackfillProgress";
import { DisconnectButton } from "@/components/connect/DisconnectDialog";
import { useTossConnection } from "@/components/connect/use-toss-connection";
import { card, primaryButton, secondaryButton, textLink } from "@/components/connect/styles";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { PageHeader } from "@/components/ui/PageHeader";
import { ApiError } from "@/lib/api/client";
import { syncToss } from "@/lib/api/account-client";
import type { TossConnection } from "@/lib/api/account-types";
import { formatDate } from "@/lib/format";
import { useMe } from "@/lib/use-me";

export function ConnectionSettings() {
  return (
    <div className="mx-auto max-w-xl">
      <PageHeader title="계좌 연결 설정" question="토스증권 연결 상태를 보고, 다시 수집하거나 연결을 해제해요." back={{ href: "/", label: "로그" }} />
      <RequireLogin next="/settings/connection">
        {/* ?rekeyed=1(키 다시 넣기 성공)은 요청마다 달라서 Suspense 안에서 읽는다. */}
        <Suspense fallback={null}>
          <RekeyedNotice />
        </Suspense>
        <SettingsBody />
      </RequireLogin>
    </div>
  );
}

/** 키 다시 넣기에서 돌아왔을 때의 알림. 나타나면 초점을 옮겨 바로 읽히게 한다. */
function RekeyedNotice() {
  const rekeyed = useSearchParams().get("rekeyed") === "1";
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (rekeyed) ref.current?.focus();
  }, [rekeyed]);
  if (!rekeyed) return null;
  return (
    <div ref={ref} tabIndex={-1} role="status" className={`${card} mb-6 space-y-1 text-sm outline-none focus-visible:outline-2 focus-visible:outline-accent`}>
      <p className="font-semibold">새 키로 바꿨어요</p>
      <p className="text-muted break-keep">다음 수집부터 새 키를 써요. 빠진 날이 있으면 아래 &lsquo;다시 수집&rsquo;으로 받아 올 수 있어요.</p>
    </div>
  );
}

const STATUS_LABEL: Record<TossConnection["status"], string> = {
  BACKFILLING: "지난 기록 만드는 중",
  ACTIVE: "연결됨",
  ERROR: "확인이 필요해요",
};

function SettingsBody() {
  const { connectionStatus } = useMe();
  const connected = connectionStatus !== "NONE";
  const { state, reload } = useTossConnection(connected);

  const notConnected =
    !connected || (state.status === "error" && (state.error.code === "ACCOUNT_NOT_CONNECTED" || state.error.status === 404));
  if (notConnected) return <NoConnection />;
  if (state.status === "loading") return <LoadingBlock height={260} label="연결 상태를 불러오는 중" />;
  if (state.status === "error") return <ErrorBlock error={state.error} onRetry={reload} />;

  const c = state.data;
  return (
    <div className="space-y-6">
      <section aria-labelledby="status-title" className={`${card} space-y-4`}>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="status-title" className="font-semibold">
            토스증권
          </h2>
          <span className="rounded-md border border-border px-2 py-0.5 text-xs font-medium">{STATUS_LABEL[c.status]}</span>
        </div>
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          <dt className="text-muted">계좌</dt>
          <dd className="font-mono">{c.maskedAccountNo ?? "-"}</dd>
          <dt className="text-muted">연결한 날</dt>
          <dd>{formatDateTime(c.connectedAt)}</dd>
          <dt className="text-muted">마지막 수집</dt>
          <dd>{formatDateTime(c.lastSyncedAt)}</dd>
        </dl>
        {c.backfill && (c.backfill.state === "RUNNING" || c.backfill.state === "PENDING") && <BackfillProgress connection={c} />}
        {c.status === "ACTIVE" && <p className="text-xs text-muted">매일 아침 7시에 전 거래일 기록을 받아 와요.</p>}
      </section>

      <section aria-labelledby="key-title" className="space-y-2 text-sm">
        <h2 id="key-title" className="font-semibold">
          토스증권 키
        </h2>
        <p className="text-muted break-keep">
          토스증권 키는 발급 1년 뒤 만료돼요. 만료일은 WTS Open API 화면에서 확인하고, 만료 전에 재발급한 뒤 &lsquo;키 다시 넣기&rsquo;를 해 주세요.
          재발급하면 예전 키는 바로 못 쓰게 돼요.
        </p>
        <Link href="/connect?mode=rekey" className={`${textLink} inline-block`}>
          키 다시 넣기
        </Link>
      </section>

      {(c.lastError || c.status === "ERROR") && <LastError message={c.lastError} error={c.status === "ERROR"} />}

      {c.recentGaps.length > 0 && (
        <section aria-labelledby="gaps-title" className={`${card} space-y-3`}>
          <h2 id="gaps-title" className="font-semibold">
            기록을 받지 못한 날
          </h2>
          <p className="text-sm text-muted break-keep">토스증권 응답이 없거나 실패해서 이 날들의 기록이 비어 있어요. 아래에서 다시 수집해 볼 수 있어요.</p>
          <ul className="flex flex-wrap gap-2 text-sm">
            {c.recentGaps.map((d) => (
              <li key={d} className="rounded-lg border border-border bg-background px-2.5 py-1 tabular-nums">
                <time dateTime={d}>{formatDate(d)}</time>
              </li>
            ))}
          </ul>
        </section>
      )}

      <SyncSection backfilling={c.status === "BACKFILLING"} onSynced={reload} />

      <section aria-labelledby="disconnect-title" className="space-y-3 border-t border-border pt-6">
        <h2 id="disconnect-title" className="font-semibold">
          연결 해제
        </h2>
        <p className="text-sm text-muted break-keep">
          해제하면 저장된 키를 바로 지워요. 쌓인 기록은 남겨 둘지 함께 지울지 고를 수 있어요.
        </p>
        <DisconnectButton maskedAccountNo={c.maskedAccountNo} />
      </section>
    </div>
  );
}

function NoConnection() {
  return (
    <section className={`${card} space-y-3`}>
      <p className="font-semibold">연결된 계좌가 없어요</p>
      <p className="text-sm text-muted break-keep">토스증권 Open API 키로 계좌를 연결하면 매일의 손익이 로그로 쌓여요.</p>
      <Link href="/connect" className={`${primaryButton} mt-2 w-full sm:w-auto`}>
        계좌 연결하기
      </Link>
    </section>
  );
}

/** 마지막 오류 + 할 일 안내. lastError는 서버가 정한 문장이라, 흔한 사유만 골라 할 일을 덧붙인다. */
function LastError({ message, error }: { message: string | null; error: boolean }) {
  const text = message ?? "";
  const ip = /IP|ip-not-allowed|허용/.test(text);
  const keyProblem = /다른 곳|token-revoked|키|만료|invalid|인증/.test(text);
  return (
    <section aria-labelledby="last-error-title" className="space-y-3 rounded-2xl border border-border p-5 text-sm">
      <h2 id="last-error-title" className="font-semibold">
        최근 문제
      </h2>
      {message && <p className="break-keep">{message}</p>}
      <p className="text-muted break-keep">
        {ip
          ? "토스증권 설정 > Open API > 허용 IP 관리에 업로그 서버 IP가 그대로 있는지 확인해 주세요. 키를 다시 넣는 화면에서 IP를 다시 볼 수 있어요."
          : keyProblem
            ? "같은 키를 다른 도구에서도 쓰거나 키를 재발급·만료했다면 업로그의 접속이 끊겨요. 업로그 전용 키를 새로 발급해 다시 넣어 주세요. 계좌와 기록은 그대로예요."
            : "잠시 뒤 다시 수집해 보세요. 계속되면 키를 재발급했거나 만료되지 않았는지 확인하고, 새 키를 다시 넣어 주세요."}
      </p>
      {(error || keyProblem || ip) && (
        <Link href="/connect?mode=rekey" className={`${primaryButton} w-full sm:w-auto`}>
          연결 다시 살리기
        </Link>
      )}
    </section>
  );
}

function SyncSection({ backfilling, onSynced }: { backfilling: boolean; onSynced: () => void }) {
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function sync() {
    setBusy(true);
    setMessage(null);
    try {
      await syncToss();
      setMessage("다시 수집을 요청했어요. 몇 분 안에 반영돼요.");
      onSynced();
    } catch (e) {
      const err = e instanceof ApiError ? e : null;
      setMessage(
        err?.status === 429
          ? "다시 수집은 1시간에 한 번 할 수 있어요. 조금 뒤에 다시 눌러 주세요."
          : (err?.message ?? "다시 수집을 요청하지 못했어요. 잠시 후 다시 시도해 주세요."),
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <section aria-labelledby="sync-title" className="space-y-3">
      <h2 id="sync-title" className="font-semibold">
        다시 수집
      </h2>
      <p id="sync-hint" className="text-sm text-muted break-keep">
        {backfilling ? "지난 기록을 만드는 동안에는 다시 수집할 수 없어요." : "가장 최근 거래일 기록을 지금 다시 받아 와요. 1시간에 한 번 할 수 있어요."}
      </p>
      <button type="button" onClick={sync} disabled={busy || backfilling} aria-describedby="sync-hint" className={`${secondaryButton} w-full sm:w-auto`}>
        {busy ? "요청하는 중…" : "다시 수집"}
      </button>
      <p aria-live="polite" className="min-h-5 text-sm">
        {message ?? ""}
      </p>
    </section>
  );
}
