import type { PortfolioDay } from "@/lib/api/portfolio-types";
import { formatPercent, formatSignedWon, formatWon, toneOf } from "@/lib/format";

const TONE_CLASS = { gain: "text-gain", loss: "text-loss", flat: "text-foreground" } as const;

/** 그날 손익·수익률·평가금액·입출금. */
export function DaySummary({ day }: { day: PortfolioDay }) {
  const tone = TONE_CLASS[toneOf(day.pnl)];
  return (
    <section aria-label="그날 요약" className="rounded-2xl bg-surface p-5">
      <p className="text-xs text-muted">하루 손익</p>
      <p className={`mt-1 text-3xl font-bold tracking-tight tabular-nums break-all ${tone}`}>{formatSignedWon(day.pnl)}</p>
      <p className={`text-sm tabular-nums ${tone}`}>{formatPercent(day.dailyReturn)}</p>
      <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-border pt-4 text-sm">
        <div>
          <dt className="text-xs text-muted">장 마감 평가금액</dt>
          <dd className="font-semibold tabular-nums">{formatWon(day.value)}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted">입출금</dt>
          <dd className="font-semibold tabular-nums">{day.netCashFlow === 0 ? "없음" : formatSignedWon(day.netCashFlow)}</dd>
        </div>
      </dl>
      {day.netCashFlow !== 0 && <p className="mt-3 text-xs text-muted break-keep">입출금은 손익에서 뺐어요(손익 = 평가금액 − 입출금 − 전날 평가금액).</p>}
    </section>
  );
}
