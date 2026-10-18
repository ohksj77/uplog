import Link from "next/link";
import type { PortfolioDay } from "@/lib/api/portfolio-types";
import { formatPercent, formatSignedWon, formatWon, toneOf } from "@/lib/format";
import { holdingHref } from "@/lib/routes";

const BAR = { gain: "var(--gain-3)", loss: "var(--loss-3)", flat: "var(--log-zero)" } as const;
const TEXT = { gain: "text-gain", loss: "text-loss", flat: "text-muted" } as const;

/** 종목별 손익 기여. 막대 길이는 그날 가장 큰 |손익| 대비. 금액에 부호가 있어 색만으로 읽지 않아도 된다. */
export function Contributions({ day }: { day: PortfolioDay }) {
  const items = [...day.contributions].sort((a, b) => Math.abs(b.pnl) - Math.abs(a.pnl));
  const max = Math.max(1, ...items.map((c) => Math.abs(c.pnl)));
  const dividends = day.cashFlows.filter((c) => c.type === "DIVIDEND").reduce((s, c) => s + c.amount, 0);
  const costs = day.trades.reduce((s, t) => s + t.fee + t.tax, 0);

  return (
    <section aria-labelledby="contrib-heading" className="space-y-3">
      <h2 id="contrib-heading" className="text-lg font-semibold">
        종목별 손익
      </h2>
      {items.length === 0 ? (
        <p className="rounded-2xl bg-surface px-5 py-8 text-center text-sm text-muted">이날 보유한 종목이 없어요</p>
      ) : (
        <ul className="space-y-1 rounded-2xl bg-surface p-2">
          {items.map((c) => {
            const tone = toneOf(c.pnl);
            return (
              <li key={c.instrument.code}>
                <Link
                  href={holdingHref(c.instrument.code)}
                  className="block rounded-xl px-3 py-2.5 hover:bg-border focus-visible:outline-2 focus-visible:outline-accent"
                >
                  <div className="flex items-baseline justify-between gap-3">
                    <span className="min-w-0 truncate text-sm font-semibold">{c.instrument.name}</span>
                    <span className={`shrink-0 text-sm font-semibold tabular-nums ${TEXT[tone]}`}>{formatSignedWon(c.pnl)}</span>
                  </div>
                  <div aria-hidden className="mt-1.5 h-2 overflow-hidden rounded-full bg-background">
                    <div className="h-full rounded-full" style={{ width: `${(Math.abs(c.pnl) / max) * 100}%`, backgroundColor: BAR[tone] }} />
                  </div>
                  <p className="mt-1 text-xs text-muted tabular-nums">
                    {formatPercent(c.dailyReturn)} · 비중 {(c.weight * 100).toFixed(1)}%
                  </p>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
      {(dividends > 0 || costs > 0) && (
        <p className="text-xs text-muted tabular-nums break-keep">
          종목별 손익 합{dividends > 0 && ` + 배당 ${formatWon(dividends)}`}
          {costs > 0 && ` − 수수료·세금 ${formatWon(costs)}`} = 하루 손익 {formatSignedWon(day.pnl)}
        </p>
      )}
    </section>
  );
}
