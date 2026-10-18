"use client";

import Link from "next/link";
import { useEffect, useRef } from "react";
import { loginHref } from "@/components/auth/next-path";
import { BackfillProgress } from "@/components/connect/BackfillProgress";
import { useTossConnection } from "@/components/connect/use-toss-connection";
import { refreshMe, useMe } from "@/lib/use-me";

const bannerLink = "shrink-0 rounded font-semibold text-accent underline-offset-4 hover:underline outline-none focus-visible:outline-2 focus-visible:outline-accent";

/**
 * 홈 위쪽 안내 띠.
 * - 샘플 계좌를 보는 비로그인 → 로그인 권유
 * - 연결했는데 계좌가 샘플(개발용 가짜 토스 서버) → 가짜 데이터라고 알림
 * - 로그인했지만 연결 전인데 샘플을 보는 경우(mock) → 연결 권유
 * - 역산 중 → 진행률. 끝나면 onBackfillDone으로 홈 데이터를 다시 부른다.
 */
export function HomeAccountBanner({ sample, onBackfillDone }: { sample: boolean; onBackfillDone: () => void }) {
  const { user, connectionStatus } = useMe();
  if (user === undefined) return null;
  if (user && connectionStatus === "BACKFILLING") return <BackfillBanner onDone={onBackfillDone} />;
  if (!sample) return null;
  if (user && connectionStatus === "ACTIVE") {
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
      <p className="break-keep">샘플 계좌를 보고 있어요.</p>
      {user ? (
        <Link href="/connect" className={bannerLink}>
          토스증권 계좌 연결하고 내 계좌로 보기
        </Link>
      ) : (
        <Link href={loginHref("/")} className={bannerLink}>
          로그인하고 내 계좌로 보기
        </Link>
      )}
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
  const { user } = useMe();
  const loggedIn = Boolean(user);
  return (
    <section aria-labelledby="connect-cta-title" className="space-y-4 rounded-2xl bg-surface p-5">
      <div>
        <h2 id="connect-cta-title" className="text-lg font-semibold">
          {loggedIn ? "토스증권 계좌를 연결해 주세요" : "로그인하고 내 계좌를 연결해 보세요"}
        </h2>
        <p className="mt-1 text-sm text-muted break-keep">연결하면 계좌를 만든 뒤의 하루하루 손익이 로그로 쌓여요.</p>
      </div>
      <ul className="space-y-1.5 text-sm text-muted break-keep">
        <li>토스증권 웹(WTS)에서 Open API 키를 발급해 붙여 넣으면 돼요. 세 단계예요.</li>
        <li>업로그는 조회만 해요. 주문하지 않아요.</li>
        <li>연결을 해제하면 키를 바로 지워요.</li>
      </ul>
      <Link
        href={loggedIn ? "/connect" : loginHref("/connect")}
        className="inline-flex h-12 w-full items-center justify-center rounded-xl bg-accent px-5 text-sm font-semibold text-white outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:w-auto"
      >
        {loggedIn ? "계좌 연결하기" : "로그인하고 연결하기"}
      </Link>
    </section>
  );
}
