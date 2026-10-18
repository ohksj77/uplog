"use client";

import { useMemo } from "react";
import { LineChart, type LineSeries } from "@/components/charts";
import { PRINCIPAL_COLOR, STRATEGY_COLOR } from "@/components/result/series";
import { STRATEGY_LABEL, type Strategy } from "@/components/result/text";
import type { Currency, SimulationResult } from "@/lib/api/types";
import { formatDate, formatMoney, formatMoneyShort } from "@/lib/format";

type ValueChartProps = {
  etfName: string;
  results: Record<Strategy, SimulationResult>;
  currency: Currency;
  selectedDate: string | null;
  onSelectDate: (date: string) => void;
};

/** 두 방식의 평가금액 + 적립식 투입 원금(점선 계단). 적립식은 첫 매수일부터 선이 시작한다. */
export function ValueChart({ etfName, results, currency, selectedDate, onSelectDate }: ValueChartProps) {
  const { labels, series } = useMemo(() => {
    const dates = [...new Set([...results.lumpSum.days, ...results.recurring.days].map((d) => d.date))].sort();
    const byDate = (s: Strategy) => new Map(results[s].days.map((d) => [d.date, d]));
    const lump = byDate("lumpSum");
    const rec = byDate("recurring");
    const series: LineSeries[] = [
      {
        id: "lumpSum",
        label: `${STRATEGY_LABEL.lumpSum} 평가금액`,
        values: dates.map((d) => lump.get(d)?.value ?? null),
        colorClassName: STRATEGY_COLOR.lumpSum,
      },
      {
        id: "recurring",
        label: `${STRATEGY_LABEL.recurring} 평가금액`,
        values: dates.map((d) => rec.get(d)?.value ?? null),
        colorClassName: STRATEGY_COLOR.recurring,
      },
      {
        id: "principal",
        label: `${STRATEGY_LABEL.recurring} 투입 원금`,
        values: dates.map((d) => rec.get(d)?.invested ?? null),
        colorClassName: PRINCIPAL_COLOR,
        dashed: true,
        step: true,
      },
    ];
    return { labels: dates, series };
  }, [results]);

  const activeIndex = selectedDate ? labels.indexOf(selectedDate) : -1;
  const lump = results.lumpSum.summary;
  const rec = results.recurring.summary;
  const description =
    `${formatDate(labels[0] ?? "")}부터 ${formatDate(labels[labels.length - 1] ?? "")}까지. ` +
    `거치식 평가금액은 ${formatMoney(lump.finalValue, currency)}, 적립식 평가금액은 ${formatMoney(rec.finalValue, currency)}, ` +
    `적립식 투입 원금은 ${formatMoney(rec.invested, currency)}이었어요.`;

  return (
    <section aria-labelledby="value-chart-heading" className="rounded-2xl border border-border p-4">
      <h2 id="value-chart-heading" className="mb-3 text-lg font-semibold">
        평가금액 흐름
      </h2>
      <LineChart
        labels={labels}
        series={series}
        formatY={(v) => formatMoneyShort(v, currency)}
        formatX={(d) => d.slice(2, 7).replace("-", ".")}
        formatTooltipTitle={formatDate}
        formatTooltipValue={(v) => formatMoney(v, currency)}
        activeIndex={activeIndex >= 0 ? activeIndex : null}
        onSelectIndex={(i) => onSelectDate(labels[i])}
        title={`${etfName} 거치식·적립식 평가금액 차트`}
        description={description}
      />
    </section>
  );
}
