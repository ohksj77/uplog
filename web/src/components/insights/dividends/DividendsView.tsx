"use client";

import { DividendHeadline } from "@/components/insights/dividends/DividendHeadline";
import { DividendMonths } from "@/components/insights/dividends/DividendMonths";
import { DividendInstruments, DividendYears } from "@/components/insights/dividends/DividendTables";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { getDividendsInsight } from "@/lib/api/portfolio-client";
import type { DividendsInsight } from "@/lib/api/portfolio-types";
import { useApi } from "@/lib/use-api";

// 배당 흐름: 요약 → 월별 막대 → 연도별 → 종목별.
export function DividendsView() {
  const { state, retry } = useApi("dividends", (signal) => getDividendsInsight(signal));
  if (state.status === "loading") return <LoadingBlock height={520} />;
  if (state.status === "error") return <ErrorBlock error={state.error} onRetry={retry} />;
  return <Body data={state.data} />;
}

function Body({ data }: { data: DividendsInsight }) {
  if (data.total === 0 || data.months.length === 0) {
    return (
      <section className="rounded-2xl bg-surface px-5 py-10 text-center">
        <p className="font-semibold">아직 받은 배당이 없어요</p>
        <p className="mt-2 text-sm text-muted">배당을 주는 종목을 보유하면 달마다 들어온 배당을 여기에 모아 보여 드려요.</p>
      </section>
    );
  }
  return (
    <div className="space-y-8">
      <DividendHeadline data={data} />
      <DividendMonths months={data.months} />
      <DividendYears byYear={data.byYear} lastMonth={data.months[data.months.length - 1].month} />
      <DividendInstruments rows={data.byInstrument} total={data.total} />
    </div>
  );
}
