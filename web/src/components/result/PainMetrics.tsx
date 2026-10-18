import type { ReactNode } from "react";
import { STRATEGY_COLOR } from "@/components/result/series";
import { Signed } from "@/components/result/Signed";
import { STRATEGIES, STRATEGY_LABEL, type Strategy } from "@/components/result/text";
import type { SimulationResult } from "@/lib/api/types";
import { formatDate, formatPercent } from "@/lib/format";

type PainMetricsProps = {
  results: Record<Strategy, SimulationResult>;
};

/** 고통 지표: 최대 손실 폭, 원금 회복 기간, 가장 아팠던 날. */
export function PainMetrics({ results }: PainMetricsProps) {
  return (
    <section aria-labelledby="pain-heading" className="space-y-3">
      <h2 id="pain-heading" className="text-lg font-semibold">
        고통 지표
      </h2>
      <div className="grid gap-3 sm:grid-cols-2">
        {STRATEGIES.map((s) => (
          <PainCard key={s} strategy={s} pain={results[s].pain} />
        ))}
      </div>
    </section>
  );
}

function PainCard({ strategy, pain }: { strategy: Strategy; pain: SimulationResult["pain"] }) {
  const { maxDrawdown, underwater, worstDay } = pain;
  return (
    <article className="rounded-2xl bg-surface p-4">
      <h3 className="flex items-center gap-2 text-sm font-semibold">
        <span aria-hidden className={`${STRATEGY_COLOR[strategy]} inline-block size-2 rounded-full bg-current`} />
        {STRATEGY_LABEL[strategy]}
      </h3>
      <dl className="mt-3 space-y-4 text-sm">
        <Metric
          label="최대 손실 폭"
          value={
            maxDrawdown.rate < 0 ? (
              <Signed value={maxDrawdown.rate}>{formatPercent(maxDrawdown.rate)}</Signed>
            ) : (
              "고점 대비 하락 없음"
            )
          }
          note={maxDrawdown.rate < 0 ? `${formatDate(maxDrawdown.peak)} 고점 → ${formatDate(maxDrawdown.trough)} 저점` : undefined}
        />
        <Metric
          label="원금 회복까지 걸린 기간"
          value={underwater == null ? "원금 아래로 내려간 적 없음" : underwater.recovered ? `${underwater.days}일` : "아직 회복 못 함"}
          note={
            underwater == null
              ? undefined
              : underwater.recovered
                ? `${formatDate(underwater.start)} → ${formatDate(underwater.end)}`
                : `${formatDate(underwater.start)}부터 ${underwater.days}일째 원금 아래`
          }
        />
        <Metric
          label="가장 아팠던 날"
          value={
            worstDay.dailyReturn < 0 ? (
              <Signed value={worstDay.dailyReturn}>{formatPercent(worstDay.dailyReturn)}</Signed>
            ) : (
              "하루도 떨어지지 않음"
            )
          }
          note={worstDay.dailyReturn < 0 ? formatDate(worstDay.date) : undefined}
        />
      </dl>
    </article>
  );
}

function Metric({ label, value, note }: { label: string; value: ReactNode; note?: string }) {
  return (
    <div>
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-0.5">
        <span className="block text-base font-semibold tabular-nums">{value}</span>
        {note && <span className="block text-xs text-muted tabular-nums">{note}</span>}
      </dd>
    </div>
  );
}
