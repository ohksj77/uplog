"use client";

import Link from "next/link";
import { useEffect, type Ref } from "react";
import { BackfillProgress } from "@/components/connect/BackfillProgress";
import { useTossConnection } from "@/components/connect/use-toss-connection";
import { card, primaryButton, secondaryButton, textLink } from "@/components/connect/styles";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { refreshMe } from "@/lib/use-me";

/** 연결 성공 화면. 역산이 끝날 때까지 2초마다 진행률을 묻는다. */
export function ConnectDone({ headingRef }: { headingRef: Ref<HTMLHeadingElement> }) {
  const { state, reload } = useTossConnection(true);
  const status = state.status === "success" ? state.data.status : null;

  // 끝나면 헤더·홈이 쓰는 연결 상태도 새로 받는다.
  useEffect(() => {
    if (status === "ACTIVE" || status === "ERROR") void refreshMe();
  }, [status]);

  return (
    <section aria-labelledby="connect-done-title" className="space-y-6">
      <h2 id="connect-done-title" ref={headingRef} tabIndex={-1} className="text-lg font-semibold outline-none">
        {status === "ERROR" ? "연결했지만 기록을 만들다 멈췄어요" : status === "ACTIVE" ? "연결을 마쳤어요" : "연결했어요"}
      </h2>

      {state.status === "loading" && <LoadingBlock height={120} label="연결 상태를 불러오는 중" />}
      {state.status === "error" && <ErrorBlock error={state.error} onRetry={reload} />}
      {state.status === "success" && (
        <div className={`${card} space-y-4`}>
          {state.data.maskedAccountNo && (
            <p className="text-sm">
              <span className="text-muted">연결한 계좌</span> <span className="font-mono font-medium">{state.data.maskedAccountNo}</span>
            </p>
          )}
          {state.data.status === "BACKFILLING" && (
            <>
              <BackfillProgress connection={state.data} />
              <p className="text-sm text-muted break-keep">
                계좌를 만든 뒤의 체결 내역과 그때의 주가·환율로 지난 기록을 만들고 있어요. 이 화면을 닫아도 계속 진행돼요.
              </p>
            </>
          )}
          {state.data.status === "ACTIVE" && (
            <p className="text-sm break-keep">지난 기록을 다 만들었어요. 이제 매일 아침 7시에 전날 기록을 더해 둘게요.</p>
          )}
          {state.data.status === "ERROR" && (
            <div className="space-y-2 text-sm">
              {state.data.lastError && <p className="font-medium break-keep">{state.data.lastError}</p>}
              <p className="text-muted break-keep">
                키와 계좌는 연결되어 있어요. 연결 설정에서 상태를 확인하고 다시 수집해 볼 수 있어요.
              </p>
              <Link href="/settings/connection" className={`${textLink} inline-block`}>
                연결 설정 보기
              </Link>
            </div>
          )}
          <p className="text-xs text-muted break-keep">
            토스증권은 입출금 내역을 주지 않아서, 연결 전 기간의 로그는 보유 종목의 손익만 보여 줘요. 연결 뒤로는 현금 변화도 함께 기록해요.
          </p>
        </div>
      )}

      <p aria-live="polite" className="sr-only">
        {status === "ACTIVE" ? "지난 기록을 다 만들었어요." : status === "ERROR" ? "기록을 만드는 중 문제가 생겼어요." : ""}
      </p>

      <Link href="/" className={`${status === "BACKFILLING" ? secondaryButton : primaryButton} w-full sm:w-auto`}>
        {status === "BACKFILLING" ? "기다리는 동안 홈으로" : "내 계좌 로그 보기"}
      </Link>
    </section>
  );
}
