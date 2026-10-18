"use client";

import { useState, type ReactNode } from "react";
import { STRATEGY_COLOR } from "@/components/result/series";
import { Signed } from "@/components/result/Signed";
import { STRATEGIES, STRATEGY_LABEL, type Strategy } from "@/components/result/text";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import type { SimulationResult } from "@/lib/api/types";
import { formatDate, formatPercent, formatSignedWon } from "@/lib/format";

/** 하락 폭·가장 아팠던 날을 무엇으로 볼지. 원금 회복 기간은 두 기준 모두 같다. */
type Basis = "rate" | "amount";

const BASIS_OPTIONS: { value: Basis; label: string }[] = [
  { value: "rate", label: "수익률 기준" },
  { value: "amount", label: "금액 기준" },
];

const BASIS_HINT: Record<Basis, string> = {
  rate: "입출금을 뺀 수익률(TWR)이에요. 같은 ETF면 거치식과 적립식이 같아요.",
  amount: "평가손익이 고점에서 얼마나 줄었는지, 하루에 얼마를 잃었는지예요. 방식마다 실제로 잃은 돈이 달라요.",
};

const STORAGE_KEY = "uplog.painBasis";

function readBasis(): Basis {
  try {
    return localStorage.getItem(STORAGE_KEY) === "amount" ? "amount" : "rate";
  } catch {
    return "rate";
  }
}

type PainMetricsProps = {
  results: Record<Strategy, SimulationResult>;
};

/** 고통 지표: 최대 손실 폭, 원금 회복 기간, 가장 아팠던 날. 하락 기준은 사용자가 고른다. */
export function PainMetrics({ results }: PainMetricsProps) {
  const [basis, setBasis] = useState<Basis>(readBasis);

  const choose = (next: Basis) => {
    setBasis(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // 저장소를 못 써도 이번 화면에서는 동작한다.
    }
  };

  return (
    <section aria-labelledby="pain-heading" className="space-y-3">
      <h2 id="pain-heading" className="text-lg font-semibold">
        고통 지표
      </h2>
      <div className="max-w-xs">
        <SegmentedControl legend="하락 기준" hideLegend options={BASIS_OPTIONS} value={basis} onChange={choose} />
      </div>
      <p className="text-xs text-muted">{BASIS_HINT[basis]}</p>
      <div className="grid gap-3 sm:grid-cols-2">
        {STRATEGIES.map((s) => (
          <PainCard key={s} strategy={s} pain={results[s].pain} basis={basis} />
        ))}
      </div>
    </section>
  );
}

function PainCard({ strategy, pain, basis }: { strategy: Strategy; pain: SimulationResult["pain"]; basis: Basis }) {
  const { underwater } = pain;
  return (
    <article className="rounded-2xl bg-surface p-4">
      <h3 className="flex items-center gap-2 text-sm font-semibold">
        <span aria-hidden className={`${STRATEGY_COLOR[strategy]} inline-block size-2 rounded-full bg-current`} />
        {STRATEGY_LABEL[strategy]}
      </h3>
      <dl className="mt-3 space-y-4 text-sm">
        {basis === "rate" ? <RateDrawdown pain={pain} /> : <AmountDrawdown pain={pain} />}
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
        {basis === "rate" ? <RateWorstDay pain={pain} /> : <AmountWorstDay pain={pain} />}
      </dl>
    </article>
  );
}

type PainProps = { pain: SimulationResult["pain"] };

function RateDrawdown({ pain: { maxDrawdown } }: PainProps) {
  const fell = maxDrawdown.rate < 0;
  return (
    <Metric
      label="최대 손실 폭"
      value={fell ? <Signed value={maxDrawdown.rate}>{formatPercent(maxDrawdown.rate)}</Signed> : "고점 대비 하락 없음"}
      note={fell ? `${formatDate(maxDrawdown.peak)} 고점 → ${formatDate(maxDrawdown.trough)} 저점` : undefined}
    />
  );
}

function AmountDrawdown({ pain: { maxDrawdownAmount } }: PainProps) {
  const fell = maxDrawdownAmount.amount < 0;
  return (
    <Metric
      label="최대 손실 폭(평가손익)"
      value={
        fell ? (
          <Signed value={maxDrawdownAmount.amount}>{formatSignedWon(maxDrawdownAmount.amount)}</Signed>
        ) : (
          "평가손익이 줄어든 적 없음"
        )
      }
      note={fell ? `${formatDate(maxDrawdownAmount.peak)} 고점 → ${formatDate(maxDrawdownAmount.trough)} 저점` : undefined}
    />
  );
}

function RateWorstDay({ pain: { worstDay } }: PainProps) {
  const fell = worstDay.dailyReturn < 0;
  return (
    <Metric
      label="가장 아팠던 날"
      value={fell ? <Signed value={worstDay.dailyReturn}>{formatPercent(worstDay.dailyReturn)}</Signed> : "하루도 떨어지지 않음"}
      note={fell ? formatDate(worstDay.date) : undefined}
    />
  );
}

function AmountWorstDay({ pain: { worstDayAmount } }: PainProps) {
  const fell = worstDayAmount.pnl < 0;
  return (
    <Metric
      label="가장 아팠던 날(하루 손익)"
      value={fell ? <Signed value={worstDayAmount.pnl}>{formatSignedWon(worstDayAmount.pnl)}</Signed> : "하루도 잃지 않음"}
      note={fell ? formatDate(worstDayAmount.date) : undefined}
    />
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
