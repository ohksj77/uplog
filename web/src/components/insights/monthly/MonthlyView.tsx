"use client";

import { useState } from "react";
import { MonthLegend } from "@/components/insights/monthly/MonthLegend";
import { MonthlyHeadline } from "@/components/insights/monthly/MonthlyHeadline";
import { MonthTable, cellKey } from "@/components/insights/monthly/MonthTable";
import { YearCards } from "@/components/insights/monthly/YearCards";
import { RETURN_THRESHOLDS, describeCell, pnlThresholds, type Basis, type MonthCell } from "@/components/insights/monthly/month-cell";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { getMonthlyInsight } from "@/lib/api/portfolio-client";
import type { MonthlyInsight } from "@/lib/api/portfolio-types";
import { useApi } from "@/lib/use-api";

const BASIS_OPTIONS: { value: Basis; label: string }[] = [
  { value: "return", label: "수익률" },
  { value: "pnl", label: "손익(원)" },
];

const BASIS_HINT: Record<Basis, string> = {
  return: "그달 일별 수익률을 이어 곱한 값(TWR)이라 입출금에 흔들리지 않아요.",
  pnl: "그달 하루 손익을 더한 금액이에요. 입금은 손익에서 빠져 있어요.",
};

export function MonthlyView() {
  const { state, retry } = useApi("monthly", (signal) => getMonthlyInsight(signal));
  if (state.status === "loading") return <LoadingBlock height={420} />;
  if (state.status === "error") return <ErrorBlock error={state.error} onRetry={retry} />;
  return <MonthlyBody data={state.data} />;
}

function MonthlyBody({ data }: { data: MonthlyInsight }) {
  const [basis, setBasis] = useState<Basis>("return");
  const [active, setActive] = useState<MonthCell | null>(null);
  const thresholds = basis === "return" ? RETURN_THRESHOLDS : pnlThresholds(data);
  const props = { data, basis, thresholds, activeKey: active ? cellKey(active) : null, onActive: setActive };

  return (
    <div className="space-y-8">
      <MonthlyHeadline data={data} />

      <section aria-labelledby="monthly-grid" className="space-y-3">
        <h2 id="monthly-grid" className="text-lg font-semibold">
          달마다 성적
        </h2>
        <div className="max-w-xs">
          <SegmentedControl legend="칸 기준" hideLegend options={BASIS_OPTIONS} value={basis} onChange={setBasis} />
        </div>
        <p className="text-xs text-muted">{BASIS_HINT[basis]}</p>
        <MonthLegend basis={basis} thresholds={thresholds} />

        {data.years.length === 0 ? (
          <p className="rounded-2xl bg-surface p-5 text-sm text-muted">아직 기록이 없어요.</p>
        ) : (
          <>
            <div className="sm:hidden">
              <YearCards {...props} />
            </div>
            <div className="hidden sm:block">
              <MonthTable {...props} />
            </div>
            <p aria-hidden className="min-h-10 rounded-lg bg-surface px-3 py-2 text-xs text-muted tabular-nums">
              {active ? (
                <>
                  <span className="font-medium text-foreground">
                    {active.year}년 {active.month}월
                  </span>{" "}
                  {describeCell(active, false)}
                </>
              ) : (
                "칸에 마우스를 올리거나 눌러 보면 그달 수익률·손익·순입금을 보여줘요."
              )}
            </p>
          </>
        )}
      </section>
    </div>
  );
}
