"use client";

import Link from "next/link";
import { useEffect, useRef } from "react";
import { BackfillProgress } from "@/components/connect/BackfillProgress";
import { useTossConnection } from "@/components/connect/use-toss-connection";
import { refreshMe, useMe } from "@/lib/use-me";

const bannerLink = "shrink-0 rounded font-semibold text-accent underline-offset-4 hover:underline outline-none focus-visible:outline-2 focus-visible:outline-accent";

/**
 * 홈 위쪽 안내 띠.
 * - 연결 전이라 샘플 계좌를 보는 중 → 연결 권유
 * - 연결했는데 계좌가 샘플(개발용 가짜 토스 서버) → 가짜 데이터라고 알림
 * - 역산 중 → 진행률. 끝나면 onBackfillDone으로 홈 데이터를 다시 부른다.
 * - 연결에 문제(ERROR) → 마지막 오류와 "연결 다시 살리기"(키 다시 넣기)
 */
export function HomeAccountBanner({ sample, onBackfillDone }: { sample: boolean; onBackfillDone: () => void }) {
  const { loading, connectionStatus } = useMe();
  if (loading) return null;
  if (connectionStatus === "BACKFILLING") return <BackfillBanner onDone={onBackfillDone} />;
  if (connectionStatus === "ERROR") return <RecoveryBanner />;
  if (!sample) return null;
  if (connectionStatus === "ACTIVE") {
    // 연결한 계좌가 샘플이면 개발용 가짜 토스 서버에서 온 데이터다
    return (
      <aside aria-label="가짜 데이터 안내" className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 rounded-2xl border-2 border-dashed border-border px-4 py-3 text-sm">
        <p className="break-keep">개발용 가짜 토스 서버에 연결된 계좌예요. 보이는 기록은 진짜 내 계좌가 아니에요.</p>
        <Link href="/settings/connection" className={bannerLink}>
          연결 설정
        </Link>
      </aside>
    );
  }
  return (
    <aside aria-label="샘플 계좌 안내" className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 rounded-2xl border border-border px-4 py-3 text-sm">
      <p className="break-keep">샘플 계좌를 보고 있어요. 연결하면 내 계좌로 바뀌어요.</p>
      <Link href="/connect" className={bannerLink}>
        토스증권 계좌 연결하기
      </Link>
    </aside>
  );
}

/** 연결이 멈췄을 때. 대개 키 문제(다른 곳에서 같은 키 사용, 재발급, 만료)라 키 다시 넣기로 바로 보낸다. */
function RecoveryBanner() {
  const { state } = useTossConnection(true);
  const lastError = state.status === "success" ? state.data.lastError : null;
  return (
    <aside aria-label="계좌 연결 문제" className="space-y-2 rounded-2xl border border-border px-4 py-3 text-sm">
      <p className="font-semibold break-keep">토스증권 연결이 멈췄어요</p>
      <p className="text-muted break-keep">
        {lastError ?? "새 기록을 받아 오지 못하고 있어요."} 키를 다른 곳에서 함께 쓰거나 재발급했다면 새 키를 넣어 연결을 살릴 수 있어요. 계좌와 기록은 그대로예요.
      </p>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
        <Link href="/connect?mode=rekey" className={bannerLink}>
          연결 다시 살리기
        </Link>
        <Link href="/settings/connection" className="shrink-0 rounded font-medium text-muted underline-offset-4 hover:text-foreground hover:underline outline-none focus-visible:outline-2 focus-visible:outline-accent">
          연결 설정
        </Link>
      </div>
    </aside>
  );
}

function BackfillBanner({ onDone }: { onDone: () => void }) {
  const { state } = useTossConnection(true);
  const status = state.status === "success" ? state.data.status : null;
  const doneRef = useRef(onDone);
  useEffect(() => {
    doneRef.current = onDone;
  });

  useEffect(() => {
    if (status === "ACTIVE" || status === "ERROR") {
      void refreshMe();
      if (status === "ACTIVE") doneRef.current();
    }
  }, [status]);

  return (
    <aside aria-label="계좌 기록 만드는 중" className="space-y-3 rounded-2xl border border-border px-4 py-3 text-sm">
      <p className="break-keep">
        토스증권 계좌의 지난 기록을 만들고 있어요. 다 만들면 이 화면이 내 계좌로 채워져요.{" "}
        <Link href="/settings/connection" className={bannerLink}>
          연결 상태 보기
        </Link>
      </p>
      {state.status === "success" && <BackfillProgress connection={state.data} />}
    </aside>
  );
}

/** 계좌가 없을 때(ACCOUNT_NOT_CONNECTED) 일반 오류 대신 보여 주는 연결 안내. */
export function ConnectCtaCard() {
  return (
    <section aria-labelledby="connect-cta-title" className="space-y-4 rounded-2xl bg-surface p-5">
      <div>
        <h2 id="connect-cta-title" className="text-lg font-semibold">
          토스증권 계좌를 연결해 주세요
        </h2>
        <p className="mt-1 text-sm text-muted break-keep">연결하면 계좌를 만든 뒤의 하루하루 손익이 로그로 쌓여요.</p>
      </div>
      <ul className="space-y-1.5 text-sm text-muted break-keep">
        <li>토스증권 웹(WTS)에서 Open API 키를 발급해 붙여 넣으면 돼요. 세 단계예요.</li>
        <li>업로그는 조회만 해요. 주문하지 않아요.</li>
        <li>키는 이 맥의 키체인으로 보호돼요. 업로그 서버는 없어요 — 키와 기록은 이 맥 밖으로 나가지 않아요.</li>
        <li>연결을 해제하면 키를 바로 지워요.</li>
      </ul>
      <Link
        href="/connect"
        className="inline-flex h-12 w-full items-center justify-center rounded-xl bg-accent px-5 text-sm font-semibold text-white outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:w-auto"
      >
        토스증권 계좌 연결하기
      </Link>
    </section>
  );
}
