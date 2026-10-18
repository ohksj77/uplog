"use client";

import { BucketSummary } from "@/components/insights/holding-periods/BucketSummary";
import { PeriodList } from "@/components/insights/holding-periods/PeriodList";
import { ReturnLegend } from "@/components/insights/holding-periods/ReturnLegend";
import { Timeline } from "@/components/insights/holding-periods/Timeline";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { getHoldingPeriodsInsight } from "@/lib/api/portfolio-client";
import type { HoldingPeriodsInsight } from "@/lib/api/portfolio-types";
import { useApi } from "@/lib/use-api";

export function HoldingPeriodsView() {
  const { state, retry } = useApi("holding-periods", (signal) => Promise.resolve().then(() => getHoldingPeriodsInsight(signal)));
  if (state.status === "loading") return <LoadingBlock height={480} />;
  if (state.status === "error") return <ErrorBlock error={state.error} onRetry={retry} />;
  return <Body data={state.data} />;
}

function Body({ data }: { data: HoldingPeriodsInsight }) {
  const { periods } = data;
  if (periods.length === 0) {
    return (
      <section className="rounded-2xl bg-surface px-5 py-10 text-center">
        <p className="font-semibold">아직 보유 기록이 없어요</p>
        <p className="mt-2 text-sm text-muted">종목을 사고팔면 포지션마다 막대로 그려 드려요.</p>
      </section>
    );
  }
  const from = periods[0].open;
  // 마지막 날: 보유 중이면 open + days, 아니면 가장 늦은 매도일
  const to = periods.reduce((max, p) => {
    const end = p.close ?? addDays(p.open, p.days);
    return end > max ? end : max;
  }, from);

  return (
    <div className="space-y-8">
      <section aria-labelledby="timeline-title" className="space-y-3">
        <h2 id="timeline-title" className="text-lg font-semibold">
          포지션 {periods.length}개
        </h2>
        <ReturnLegend />
        <Timeline periods={periods} from={from} to={to} />
        <details className="rounded-2xl bg-surface px-4 py-3">
          <summary className="cursor-pointer text-sm font-medium">포지션 목록으로 보기</summary>
          <PeriodList periods={periods} />
        </details>
      </section>
      <BucketSummary buckets={data.buckets} />
    </div>
  );
}

function addDays(date: string, days: number): string {
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}
