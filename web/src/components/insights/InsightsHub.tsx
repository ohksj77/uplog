"use client";

import Link from "next/link";
import { ErrorBlock } from "@/components/ui/AsyncState";
import { getInsightAvailability } from "@/lib/api/portfolio-client";
import type { InsightAvailability, InsightKey } from "@/lib/api/portfolio-types";
import { useApi } from "@/lib/use-api";

const INSIGHTS: { key: InsightKey; title: string; question: string }[] = [
  { key: "sales", title: "판 뒤의 그림자", question: "그때 판 게 잘한 일이었나" },
  { key: "shadow", title: "그림자 계좌", question: "종목 고르기가 지수보다 나았나" },
  { key: "waterfall", title: "손익 폭포수", question: "누가 내 수익을 만들고 깎았나" },
  { key: "holding-periods", title: "보유 기간 타임라인", question: "단타와 장투 중 뭘 잘하나" },
  { key: "fx", title: "수익 분해", question: "이 수익은 종목 덕인가 환율 덕인가" },
  { key: "underwater", title: "수중 차트", question: "지금 하락은 전에 버틴 것보다 큰가" },
  { key: "monthly", title: "월별 성적표", question: "어느 달에 벌고 어느 달에 잃었나" },
  { key: "habits", title: "매매 습관", question: "수익은 빨리 팔고 손실은 오래 버티나" },
  { key: "dividends", title: "배당 흐름", question: "매달 배당이 얼마 들어오나" },
  { key: "costs", title: "숨은 비용", question: "올해 비용으로 얼마 나갔나" },
];

const cardClass =
  "block rounded-2xl bg-surface p-4 transition-colors hover:bg-border focus-visible:outline-2 focus-visible:outline-accent";

/** 분석 목록. 이 계좌에 해당하지 않는 분석(매도 없음, 해외 종목 없음, 배당 내역을 알 수 없음 등)은 따로 모아 이유와 함께 보여 준다. */
export function InsightsHub() {
  const { state, retry } = useApi("insights-availability", (signal) => getInsightAvailability(signal));
  if (state.status === "error") return <ErrorBlock error={state.error} onRetry={retry} />;

  // 불러오는 동안은 모두 보여 주고, 응답이 오면 해당하지 않는 것을 아래로 뺀다.
  const byKey = new Map<InsightKey, InsightAvailability>(
    state.status === "success" ? state.data.map((a) => [a.key, a]) : [],
  );
  const applicable = INSIGHTS.filter((i) => byKey.get(i.key)?.available !== false);
  const notApplicable = INSIGHTS.filter((i) => byKey.get(i.key)?.available === false);

  return (
    <div className="space-y-8">
      <ul className="grid gap-3 sm:grid-cols-2" aria-busy={state.status === "loading"}>
        {applicable.map((i) => (
          <li key={i.key}>
            <Link href={`/insights/${i.key}`} className={cardClass}>
              <span className="block font-semibold">{i.title}</span>
              <span className="mt-1 block text-sm text-muted">{i.question}</span>
            </Link>
          </li>
        ))}
      </ul>
      {notApplicable.length > 0 && (
        <details className="rounded-2xl border border-border p-4">
          <summary className="cursor-pointer text-sm font-semibold">내 계좌에 해당하지 않는 분석 {notApplicable.length}개</summary>
          <ul className="mt-3 space-y-3">
            {notApplicable.map((i) => (
              <li key={i.key} className="text-sm">
                <span className="font-medium">{i.title}</span>
                <span className="mt-0.5 block text-muted break-keep">{byKey.get(i.key)?.reason}</span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}
