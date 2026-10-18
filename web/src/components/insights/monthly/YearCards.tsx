import { cellKey } from "@/components/insights/monthly/MonthTable";
import { cellStyle, describeCell, formatValue, type Basis, type MonthCell } from "@/components/insights/monthly/month-cell";
import type { LogThresholds } from "@/components/log-grid/logic";
import type { MonthlyInsight } from "@/lib/api/portfolio-types";
import { formatPercent, formatSignedWon } from "@/lib/format";
import { Signed } from "@/components/result/Signed";

// 좁은 화면용: 해마다 4 × 3 칸. 최근 해가 위.

type Props = {
  data: MonthlyInsight;
  basis: Basis;
  thresholds: LogThresholds;
  activeKey: string | null;
  onActive: (cell: MonthCell | null) => void;
};

export function YearCards({ data, basis, thresholds, activeKey, onActive }: Props) {
  const years = [...data.years].reverse();
  return (
    <div className="space-y-5">
      {years.map((y) => {
        const total = basis === "return" ? y.total.return : y.total.pnl;
        return (
          <section key={y.year} aria-labelledby={`month-year-${y.year}`} className="space-y-2">
            <div className="flex items-baseline justify-between gap-2">
              <h3 id={`month-year-${y.year}`} className="font-semibold">
                {y.year}년
              </h3>
              <p className="text-sm tabular-nums">
                <span className="text-muted">연간 </span>
                <Signed value={total} className="font-semibold">
                  {basis === "return" ? formatPercent(total, 1) : formatSignedWon(total)}
                </Signed>
              </p>
            </div>
            <ol className="grid grid-cols-4 gap-1">
              {y.months.map((m) => {
                const cell = { ...m, year: y.year };
                const value = basis === "return" ? m.return : m.pnl;
                const recorded = m.return != null;
                return (
                  <li
                    key={m.month}
                    tabIndex={recorded ? 0 : undefined}
                    title={describeCell(cell)}
                    onFocus={() => onActive(cell)}
                    onBlur={() => onActive(null)}
                    className={`flex h-12 flex-col items-center justify-center rounded-md text-xs tabular-nums outline-none focus-visible:ring-2 focus-visible:ring-accent ${
                      activeKey === cellKey(cell) ? "ring-2 ring-foreground" : ""
                    }`}
                    style={cellStyle(value, basis, thresholds)}
                  >
                    <span aria-hidden className="opacity-80">
                      {m.month}월
                    </span>
                    <span aria-hidden className="font-semibold">
                      {formatValue(value, basis)}
                    </span>
                    <span className="sr-only">{describeCell(cell)}</span>
                  </li>
                );
              })}
            </ol>
          </section>
        );
      })}
    </div>
  );
}
