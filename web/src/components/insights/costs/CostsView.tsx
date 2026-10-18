"use client";

import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { CostHeadline } from "@/components/insights/costs/CostHeadline";
import { CostMonths } from "@/components/insights/costs/CostMonths";
import { CostTable } from "@/components/insights/costs/CostTable";
import { YearTabs } from "@/components/ui/YearTabs";
import { formatManShort, formatWonTenth } from "@/components/insights/shared/money";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { getCostsInsight } from "@/lib/api/portfolio-client";
import type { CostsInsight } from "@/lib/api/portfolio-types";
import { useApi } from "@/lib/use-api";

/** "?year=2025" → 2025. 없거나 이상하면 null(엔진 기본 = 마지막 기록 연도). */
function parseYear(raw: string | null): number | null {
  return raw && /^\d{4}$/.test(raw) ? Number(raw) : null;
}

// 숨은 비용: 연도 → 요약 → 월별 막대 → 종목별 → 전체 기간 합.
export function CostsView() {
  const year = parseYear(useSearchParams().get("year"));
  const { state, retry } = useApi(`costs:${year ?? "latest"}`, (signal) => getCostsInsight(year, signal));

  // 다른 해를 불러오는 동안에도 연도 탭이 사라지지 않게 마지막으로 받은 목록을 기억한다.
  const [years, setYears] = useState<number[]>([]);
  const [latestYear, setLatestYear] = useState<number | null>(null);
  if (state.status === "success" && state.data.availableYears.join() !== years.join()) {
    setYears(state.data.availableYears);
    setLatestYear(state.data.availableYears[state.data.availableYears.length - 1] ?? null);
  }
  const selected = state.status === "success" ? state.data.year : (year ?? latestYear);

  return (
    <div className="space-y-6">
      {years.length > 1 && selected != null && <YearTabs years={years} selected={selected} basePath="/insights/costs" />}
      {state.status === "loading" && <LoadingBlock height={520} />}
      {state.status === "error" && <ErrorBlock error={state.error} onRetry={retry} />}
      {state.status === "success" && <Body data={state.data} isLatestYear={state.data.year === latestYear} />}
    </div>
  );
}

function Body({ data, isLatestYear }: { data: CostsInsight; isLatestYear: boolean }) {
  const { allTime } = data;
  return (
    <div className="space-y-8">
      <CostHeadline data={data} isLatestYear={isLatestYear} />
      {data.months.length > 0 && <CostMonths year={data.year} months={data.months} />}
      {data.byInstrument.length > 0 && <CostTable rows={data.byInstrument} total={data.total} />}
      <section aria-label="전체 기간" className="space-y-2 rounded-2xl border border-border p-4 text-sm">
        <p className="break-keep">
          기록 전체로는 비용이 <span className="font-semibold tabular-nums">{formatWonTenth(allTime.cost)}</span> 나갔어요
          <span className="text-muted">
            (수수료 {formatManShort(allTime.fee)} · 세금 {formatManShort(allTime.tax)})
          </span>
          .
        </p>
        <p className="text-xs text-muted break-keep">
          비용은 체결 수수료와 세금(매도 거래세 등)만 셌어요. 달러를 살 때 붙는 환전 스프레드는 아직 데이터가 없어 넣지 않았어요.
        </p>
      </section>
    </div>
  );
}
