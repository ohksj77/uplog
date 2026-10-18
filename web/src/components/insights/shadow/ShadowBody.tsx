import type { ReactNode } from "react";
import { LineChart } from "@/components/charts";
import { SignedAreaChart } from "@/components/charts/SignedAreaChart";
import { Signed } from "@/components/result/Signed";
import type { ShadowInsight } from "@/lib/api/portfolio-types";
import { formatDate, formatPercent, formatSignedWon, formatWon, formatWonApprox, formatWonShort } from "@/lib/format";

const ACTUAL_COLOR = "text-series-lump";
const SHADOW_COLOR = "text-series-recurring";

const monthTick = (d: string) => `${d.slice(2, 4)}.${d.slice(5, 7)}`;

export function ShadowBody({ data }: { data: ShadowInsight }) {
  const { series, summary } = data;
  const name = data.benchmarks.find((b) => b.code === data.benchmark)?.name ?? data.benchmark;
  const labels = series.map((p) => p.date);
  const first = labels[0];
  const last = labels[labels.length - 1];

  return (
    <div className="space-y-8">
      <Headline name={name} summary={summary} />
      <SummaryGrid name={name} summary={summary} />

      <section aria-labelledby="shadow-chart" className="space-y-3">
        <h2 id="shadow-chart" className="text-lg font-semibold">
          평가금 비교
        </h2>
        <LineChart
          labels={labels}
          series={[
            { id: "actual", label: "내 계좌", values: series.map((p) => p.actual), colorClassName: ACTUAL_COLOR },
            { id: "shadow", label: `${name}에 넣었다면`, values: series.map((p) => p.shadow), colorClassName: SHADOW_COLOR },
            {
              id: "deposits",
              label: "순입금",
              values: series.map((p) => p.netDeposits),
              colorClassName: "text-muted",
              dashed: true,
              step: true,
            },
          ]}
          formatY={formatWonShort}
          formatX={monthTick}
          formatTooltipTitle={formatDate}
          formatTooltipValue={formatWon}
          title={`내 계좌와 ${name} 그림자 계좌의 평가금`}
          description={`${formatDate(first)}부터 ${formatDate(last)}까지. 마지막 날 내 계좌 ${formatWon(summary.actual)}, ${name}에 넣었다면 ${formatWon(summary.shadow)}, 순입금 ${formatWon(series[series.length - 1]?.netDeposits ?? 0)}.`}
          height={260}
        />
      </section>

      <section aria-labelledby="shadow-diff" className="space-y-3">
        <div>
          <h2 id="shadow-diff" className="text-lg font-semibold">
            차이의 흐름
          </h2>
          <p className="mt-1 text-xs text-muted">내 계좌 − {name} 그림자 계좌. 위(빨강)면 내가 앞서고, 아래(파랑)면 지수가 앞서요.</p>
        </div>
        <SignedAreaChart
          labels={labels}
          values={series.map((p) => p.actual - p.shadow)}
          valueLabel="내 계좌 − 지수"
          formatY={formatWonShort}
          formatX={monthTick}
          formatTooltipTitle={formatDate}
          formatTooltipValue={formatSignedWon}
          title={`내 계좌와 ${name} 그림자 계좌의 차이`}
          description={differenceSummary(series, name)}
          height={180}
        />
      </section>

      <p className="text-xs leading-relaxed text-muted">
        실제 입금·출금 날짜와 금액 그대로 그날 종가에 {name} 지수를 사고팔았다고 계산했어요.
        {data.benchmark === "SPX" && " S&P500은 그날 원/달러 환율로 원화로 바꿔 넣었어요."} 지수 자체를 따라가므로 수수료·세금은
        빠져 있어요.
      </p>
    </div>
  );
}

function Headline({ name, summary }: { name: string; summary: ShadowInsight["summary"] }) {
  const { difference } = summary;
  const gap = formatWonApprox(Math.abs(difference));
  const main =
    difference < 0
      ? `같은 돈을 ${name}에 넣었다면 ${gap} 더 많았어요.`
      : difference > 0
        ? `같은 돈을 ${name}에 넣었을 때보다 ${gap} 앞서요.`
        : `${name}에 넣었을 때와 똑같아요.`;
  return (
    <section aria-label="요약" className="rounded-2xl bg-surface p-5">
      <p className="text-lg leading-snug font-semibold break-keep">
        <Signed value={difference}>{main}</Signed>
      </p>
      <p className="mt-2 text-sm text-muted break-keep">
        수익률(TWR)로는 내 계좌 {formatPercent(summary.actualTwr)}, {name} {formatPercent(summary.shadowTwr)}
        {summary.actualTwr >= summary.shadowTwr ? "로 내가 앞서요." : "로 지수가 앞서요."}
      </p>
    </section>
  );
}

function SummaryGrid({ name, summary }: { name: string; summary: ShadowInsight["summary"] }) {
  const twrGap = summary.actualTwr - summary.shadowTwr;
  return (
    <dl className="grid grid-cols-2 gap-3">
      <Stat label="내 계좌" value={formatWon(summary.actual)} note={`수익률 ${formatPercent(summary.actualTwr)}`} dot={ACTUAL_COLOR} />
      <Stat label={`${name}에 넣었다면`} value={formatWon(summary.shadow)} note={`수익률 ${formatPercent(summary.shadowTwr)}`} dot={SHADOW_COLOR} />
      <Stat
        label="금액 차이"
        value={<Signed value={summary.difference}>{formatSignedWon(summary.difference)}</Signed>}
        note="내 계좌 − 지수"
      />
      <Stat
        label="수익률 차이"
        value={<Signed value={twrGap}>{formatPercent(twrGap)}p</Signed>}
        note="입출금 영향을 뺀 값"
      />
    </dl>
  );
}

function Stat({ label, value, note, dot }: { label: string; value: ReactNode; note: string; dot?: string }) {
  return (
    <div className="min-w-0 rounded-2xl bg-surface p-4">
      <dt className="flex items-center gap-1.5 text-xs text-muted">
        {dot && <span aria-hidden className={`${dot} inline-block size-2 rounded-full bg-current`} />}
        {label}
      </dt>
      <dd className="mt-1">
        <span className="block text-base font-semibold break-all tabular-nums">{value}</span>
        <span className="block text-xs text-muted">{note}</span>
      </dd>
    </div>
  );
}

function differenceSummary(series: ShadowInsight["series"], name: string): string {
  const ahead = series.filter((p) => p.actual > p.shadow).length;
  const diffs = series.map((p) => p.actual - p.shadow);
  return `전체 ${series.length}거래일 중 ${ahead}일은 내 계좌가 ${name}보다 앞섰어요. 가장 크게 앞선 날 ${formatSignedWon(Math.max(...diffs))}, 가장 크게 뒤진 날 ${formatSignedWon(Math.min(...diffs))}.`;
}
