import type { ReactNode } from "react";
import { STRATEGY_COLOR } from "@/components/result/series";
import { Signed } from "@/components/result/Signed";
import { STRATEGIES, STRATEGY_LABEL, verdict, type Strategy } from "@/components/result/text";
import type { SimulationResult } from "@/lib/api/types";
import { formatPercent, formatSignedWon, formatWon } from "@/lib/format";

type SummaryCardsProps = {
  results: Record<Strategy, SimulationResult>;
};

export function SummaryCards({ results }: SummaryCardsProps) {
  return (
    <section aria-labelledby="summary-heading" className="space-y-3">
      <h2 id="summary-heading" className="text-lg font-semibold">
        결과 요약
      </h2>
      <p className="rounded-2xl bg-surface px-4 py-3 text-base font-medium break-keep">
        {verdict(results.lumpSum, results.recurring)}
      </p>
      <div className="grid gap-3 sm:grid-cols-2">
        {STRATEGIES.map((s) => (
          <SummaryCard key={s} strategy={s} result={results[s]} />
        ))}
      </div>
    </section>
  );
}

function SummaryCard({ strategy, result }: { strategy: Strategy; result: SimulationResult }) {
  const { finalValue, invested, profit, totalReturn, annualizedTwr } = result.summary;
  return (
    <article className="rounded-2xl border border-border p-4">
      <h3 className="flex items-center gap-2 text-sm font-semibold">
        <span aria-hidden className={`${STRATEGY_COLOR[strategy]} inline-block size-2 rounded-full bg-current`} />
        {STRATEGY_LABEL[strategy]}
      </h3>
      <p className="mt-2 text-xs text-muted">최종 평가금액</p>
      <p className="text-2xl font-bold tabular-nums break-all">{formatWon(finalValue)}</p>
      <Signed value={profit} className="text-sm font-semibold tabular-nums">
        {formatSignedWon(profit)} ({formatPercent(totalReturn)})
      </Signed>
      <dl className="mt-4 space-y-2 border-t border-border pt-3 text-sm">
        <Item label="총 투입금">{formatWon(invested)}</Item>
        <Item label="손익">
          <Signed value={profit}>{formatSignedWon(profit)}</Signed>
        </Item>
        <Item label="총 수익률">
          <Signed value={totalReturn}>{formatPercent(totalReturn)}</Signed>
        </Item>
        <Item label="연환산 수익률(TWR)">
          <Signed value={annualizedTwr}>{formatPercent(annualizedTwr)}</Signed>
        </Item>
      </dl>
    </article>
  );
}

function Item({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <dt className="text-muted">{label}</dt>
      <dd className="text-right font-medium tabular-nums">{children}</dd>
    </div>
  );
}
