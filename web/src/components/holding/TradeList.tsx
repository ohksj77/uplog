import { formatFx, formatLocal } from "@/components/holding/format";
import type { Trade } from "@/lib/api/portfolio-types";
import { formatDate, formatWon } from "@/lib/format";

// 체결 내역, 최근 것 먼저.

export function TradeList({ trades }: { trades: Trade[] }) {
  const rows = [...trades].reverse();
  return (
    <ul className="divide-y divide-border rounded-2xl bg-surface px-4">
      {rows.map((t, i) => {
        const buy = t.side === "BUY";
        const costs = t.fee + t.tax;
        return (
          <li key={`${t.date}-${i}`} className="flex items-start justify-between gap-3 py-3 text-sm">
            <div className="min-w-0">
              <p>
                <span className={`mr-2 font-semibold ${buy ? "text-trade-buy" : "text-trade-sell"}`}>{buy ? "매수" : "매도"}</span>
                <span className="text-muted tabular-nums">{formatDate(t.date)}</span>
              </p>
              <p className="mt-0.5 text-xs text-muted tabular-nums">
                {t.quantity.toLocaleString("ko-KR")}주 × {formatLocal(t.priceLocal, t.instrument.currency)}
                {t.instrument.currency === "USD" && ` · 환율 ${formatFx(t.fxRate)}`}
                {costs > 0 && ` · 수수료·세금 ${formatWon(costs)}`}
              </p>
            </div>
            <span className="shrink-0 font-medium tabular-nums">{formatWon(t.amount)}</span>
          </li>
        );
      })}
    </ul>
  );
}
