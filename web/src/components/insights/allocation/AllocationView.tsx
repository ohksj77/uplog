"use client";

import { useState } from "react";
import { StackedAreaChart, type StackedSeries } from "@/components/charts/StackedAreaChart";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { getAllocationInsight } from "@/lib/api/portfolio-client";
import type { AllocationInsight } from "@/lib/api/portfolio-types";
import { formatDate, formatWon, formatWonShort } from "@/lib/format";
import { useApi } from "@/lib/use-api";

// 자산 구성 변화: 시기별로 돈이 어느 종목·국내·해외·현금에 있었나.

type View = "instrument" | "market";

const VIEW_OPTIONS: { value: View; label: string }[] = [
  { value: "instrument", label: "종목별" },
  { value: "market", label: "국내·해외" },
];

/** 범주 색은 정해진 순서로 쓴다(종목 순위가 아니라 그룹 위치를 따른다). */
const CATEGORY_COLORS = ["var(--cat-1)", "var(--cat-2)", "var(--cat-3)", "var(--cat-4)", "var(--cat-5)"];

const pct = (w: number) => `${(w * 100).toFixed(1)}%`;
const monthTick = (d: string) => `${d.slice(2, 4)}.${d.slice(5, 7)}`;

export function AllocationView() {
  const { state, retry } = useApi("allocation", (signal) => getAllocationInsight(signal));
  const [view, setView] = useState<View>("instrument");
  if (state.status === "loading") return <LoadingBlock height={480} />;
  if (state.status === "error") return <ErrorBlock error={state.error} onRetry={retry} />;
  const data = state.data;
  if (data.points.length === 0 || data.peak == null) {
    return (
      <section className="rounded-2xl bg-surface px-5 py-10 text-center">
        <p className="font-semibold">보유했던 종목이 없어요</p>
        <p className="mt-2 text-sm text-muted">종목을 사면 시기별로 돈이 어디에 있었는지 여기에 쌓아 보여 드려요.</p>
      </section>
    );
  }
  const series = view === "instrument" ? instrumentSeries(data) : marketSeries(data);
  const labels = data.points.map((p) => p.date);

  return (
    <div className="space-y-8">
      <Headline data={data} />
      <section aria-labelledby="allocation-chart" className="space-y-3">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <h2 id="allocation-chart" className="text-lg font-semibold">
            시기별 구성
          </h2>
          <div className="w-56">
            <SegmentedControl legend="나눠 보기" hideLegend options={VIEW_OPTIONS} value={view} onChange={setView} />
          </div>
        </div>
        <Legend series={series} />
        <StackedAreaChart
          labels={labels}
          series={series}
          formatY={formatWonShort}
          formatX={monthTick}
          formatTitle={formatDate}
          formatValue={(v, share) => `${formatWonShort(v)} · ${pct(share)}`}
          title="시기별 자산 구성"
          description={describe(data, series)}
        />
        <p className="text-xs text-muted">주마다 마지막 기록일 기준이에요. 차트에 마우스를 올리거나 방향키로 옮기면 그날 구성을 보여 줘요.</p>
      </section>
      <CurrentTable data={data} />
    </div>
  );
}

function instrumentSeries(data: AllocationInsight): StackedSeries[] {
  let slot = 0;
  return data.groups.map((g, k) => ({
    key: g.key,
    label: g.label,
    color: g.kind === "INSTRUMENT" ? CATEGORY_COLORS[slot++ % CATEGORY_COLORS.length] : g.kind === "OTHER" ? "var(--cat-other)" : "var(--cat-cash)",
    values: data.points.map((p) => p.values[k] ?? 0),
  }));
}

function marketSeries(data: AllocationInsight): StackedSeries[] {
  return [
    { key: "kr", label: "국내", color: "var(--cat-1)", values: data.points.map((p) => p.kr) },
    { key: "us", label: "해외", color: "var(--cat-2)", values: data.points.map((p) => p.us) },
    { key: "cash", label: "현금", color: "var(--cat-cash)", values: data.points.map((p) => p.cash) },
  ];
}

function Headline({ data }: { data: AllocationInsight }) {
  const last = data.points[data.points.length - 1];
  const top = data.current[0];
  const topLabel = data.groups.find((g) => g.key === top?.key)?.label;
  const share = (v: number) => (last.total > 0 ? pct(v / last.total) : "0%");
  return (
    <section className="space-y-2 rounded-2xl bg-surface p-5">
      <p className="text-lg font-semibold break-keep">
        지금은 국내 {share(last.kr)} · 해외 {share(last.us)} · 현금 {share(last.cash)}이에요.
      </p>
      <p className="text-sm text-muted break-keep">
        {top && topLabel ? `가장 큰 비중은 ${topLabel}(${pct(top.weight)})예요. ` : ""}
        {data.peak && `한 종목에 가장 많이 몰렸던 때는 ${formatDate(data.peak.date)} ${data.peak.label}(${pct(data.peak.weight)})예요.`}
      </p>
    </section>
  );
}

function Legend({ series }: { series: StackedSeries[] }) {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted" aria-label="범례">
      {series.map((s) => (
        <li key={s.key} className="flex items-center gap-1.5">
          <span aria-hidden className="inline-block size-2.5 rounded-sm" style={{ background: s.color }} />
          {s.label}
        </li>
      ))}
    </ul>
  );
}

/** 지금 구성 표. 색 대비가 낮은 범주가 있어 숫자를 표로도 준다. */
function CurrentTable({ data }: { data: AllocationInsight }) {
  return (
    <section aria-labelledby="allocation-now" className="space-y-3">
      <h2 id="allocation-now" className="text-lg font-semibold">
        지금 구성 <span className="text-sm font-normal text-muted">{formatDate(data.asOf)} 기준</span>
      </h2>
      <div className="overflow-hidden rounded-2xl border border-border">
        <table className="w-full text-sm">
          <thead className="bg-surface text-xs text-muted">
            <tr>
              <th scope="col" className="px-4 py-2 text-left font-medium">
                구분
              </th>
              <th scope="col" className="px-4 py-2 text-right font-medium">
                평가금액
              </th>
              <th scope="col" className="px-4 py-2 text-right font-medium">
                비중
              </th>
            </tr>
          </thead>
          <tbody>
            {data.current.map((c) => {
              const g = data.groups.find((x) => x.key === c.key);
              return (
                <tr key={c.key} className="border-t border-border">
                  <td className="px-4 py-2">
                    {g?.label ?? c.key}
                    {g?.market === "US" && <span className="ml-1 text-xs text-muted">해외</span>}
                  </td>
                  <td className="px-4 py-2 text-right whitespace-nowrap tabular-nums">{formatWon(c.value)}</td>
                  <td className="px-4 py-2 text-right whitespace-nowrap tabular-nums">{pct(c.weight)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}

function describe(data: AllocationInsight, series: StackedSeries[]): string {
  const first = data.points[0];
  const last = data.points[data.points.length - 1];
  return `${formatDate(first.date)}부터 ${formatDate(last.date)}까지 주마다 ${series.map((s) => s.label).join(", ")}의 평가금액을 쌓은 차트. 마지막 총평가금 ${formatWon(last.total)}.`;
}
