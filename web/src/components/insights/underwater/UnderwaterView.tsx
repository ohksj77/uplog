"use client";

import { useState } from "react";
import { EpisodeTable } from "@/components/insights/underwater/EpisodeTable";
import { SignedAreaChart } from "@/components/charts/SignedAreaChart";
import { UnderwaterHeadline } from "@/components/insights/underwater/UnderwaterHeadline";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { getUnderwaterInsight } from "@/lib/api/portfolio-client";
import type { UnderwaterInsight } from "@/lib/api/portfolio-types";
import { formatDate, formatPercent, formatSignedWon, formatWonShort } from "@/lib/format";
import { useApi } from "@/lib/use-api";

/** 하락을 수익률(TWR)로 볼지, 평가손익 금액으로 볼지. 고통 지표와 같은 방식. */
export type Basis = "rate" | "amount";

const BASIS_OPTIONS: { value: Basis; label: string }[] = [
  { value: "rate", label: "수익률 기준" },
  { value: "amount", label: "금액 기준" },
];

const BASIS_HINT: Record<Basis, string> = {
  rate: "입출금을 뺀 수익률(TWR)이 직전 고점에서 얼마나 내려왔는지예요.",
  amount: "평가손익(평가금 − 순입금)이 직전 고점에서 얼마나 줄었는지예요. 계좌가 클수록 같은 하락도 커 보여요.",
};

const STORAGE_KEY = "uplog.underwaterBasis";

function readBasis(): Basis {
  try {
    return localStorage.getItem(STORAGE_KEY) === "amount" ? "amount" : "rate";
  } catch {
    return "rate";
  }
}

export function UnderwaterView() {
  const { state, retry } = useApi("underwater", (signal) => getUnderwaterInsight(signal));
  // 첫 렌더는 항상 불러오는 중이라 저장값을 바로 읽어도 서버 HTML과 어긋나지 않는다.
  const [basis, setBasis] = useState<Basis>(readBasis);

  const choose = (next: Basis) => {
    setBasis(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // 저장소를 못 써도 이번 화면에서는 동작한다.
    }
  };

  if (state.status === "loading") return <LoadingBlock height={420} />;
  if (state.status === "error") return <ErrorBlock error={state.error} onRetry={retry} />;
  return <UnderwaterBody data={state.data} basis={basis} onBasisChange={choose} />;
}

function UnderwaterBody({
  data,
  basis,
  onBasisChange,
}: {
  data: UnderwaterInsight;
  basis: Basis;
  onBasisChange: (b: Basis) => void;
}) {
  const { series, current } = data;
  const labels = series.map((p) => p.date);
  const values = series.map((p) => (basis === "rate" ? p.drawdown : p.drawdownAmount));
  const underNow = current.drawdown < 0;
  const peakIndex = underNow ? labels.indexOf(current.peak) : -1;
  const format = basis === "rate" ? (v: number) => formatPercent(v, 1) : formatWonShort;
  const tooltip = basis === "rate" ? (v: number) => formatPercent(v) : formatSignedWon;
  const deepest = Math.min(0, ...values);

  return (
    <div className="space-y-8">
      <UnderwaterHeadline data={data} basis={basis} />

      <section aria-labelledby="uw-chart" className="space-y-3">
        <h2 id="uw-chart" className="text-lg font-semibold">
          고점 대비 하락
        </h2>
        <div className="max-w-xs">
          <SegmentedControl legend="하락 기준" hideLegend options={BASIS_OPTIONS} value={basis} onChange={onBasisChange} />
        </div>
        <p className="text-xs text-muted">{BASIS_HINT[basis]}</p>
        <SignedAreaChart
          labels={labels}
          values={values}
          valueLabel="고점 대비"
          formatY={format}
          formatX={(d) => `${d.slice(2, 4)}.${d.slice(5, 7)}`}
          formatTooltipTitle={formatDate}
          formatTooltipValue={tooltip}
          highlight={peakIndex >= 0 ? { from: peakIndex, to: labels.length - 1, label: "지금 하락" } : null}
          reference={
            basis === "rate" && current.deepestPast < 0
              ? { value: current.deepestPast, label: `과거 최저 ${formatPercent(current.deepestPast, 1)}` }
              : null
          }
          title={basis === "rate" ? "수익률 기준 수중 차트" : "금액 기준 수중 차트"}
          description={`${formatDate(labels[0])}부터 ${formatDate(labels[labels.length - 1])}까지 직전 고점 대비 하락. 가장 깊었던 값 ${tooltip(deepest)}, 마지막 날 ${tooltip(values[values.length - 1])}.`}
        />
      </section>

      <EpisodeTable episodes={data.episodes} />
    </div>
  );
}
