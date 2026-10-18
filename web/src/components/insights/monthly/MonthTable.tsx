import { cellStyle, describeCell, formatValue, type Basis, type MonthCell } from "@/components/insights/monthly/month-cell";
import type { LogThresholds } from "@/components/log-grid/logic";
import type { MonthlyInsight } from "@/lib/api/portfolio-types";
import { formatPercent, formatSignedWon } from "@/lib/format";

// 연 × 월 표(넓은 화면). 최근 해가 위. 좁으면 표 안에서만 가로로 밀리고 연도 열은 붙어 있다.

const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1);

type Props = {
  data: MonthlyInsight;
  basis: Basis;
  thresholds: LogThresholds;
  activeKey: string | null;
  onActive: (cell: MonthCell | null) => void;
};

export const cellKey = (c: { year: number; month: number }) => `${c.year}-${c.month}`;

export function MonthTable({ data, basis, thresholds, activeKey, onActive }: Props) {
  const years = [...data.years].reverse();
  return (
    <div className="overflow-x-auto rounded-2xl border border-border" onMouseLeave={() => onActive(null)}>
      <table className="w-full min-w-[46rem] border-separate border-spacing-0.5 p-1 text-xs tabular-nums">
        <caption className="sr-only">
          월별 {basis === "return" ? "수익률" : "손익"}. 행은 연도, 열은 월이고 마지막 열은 그해 합계예요.
        </caption>
        <thead className="text-muted">
          <tr>
            <th scope="col" className="sticky left-0 z-10 bg-background px-2 py-1.5 text-left font-medium">
              연도
            </th>
            {MONTHS.map((m) => (
              <th key={m} scope="col" className="py-1.5 font-medium">
                {m}월
              </th>
            ))}
            <th scope="col" className="px-2 py-1.5 text-right font-medium">
              연간
            </th>
          </tr>
        </thead>
        <tbody>
          {years.map((y) => {
            const total = basis === "return" ? y.total.return : y.total.pnl;
            return (
              <tr key={y.year}>
                <th scope="row" className="sticky left-0 z-10 bg-background px-2 text-left font-semibold">
                  {y.year}
                </th>
                {y.months.map((m) => {
                  const cell = { ...m, year: y.year };
                  const value = basis === "return" ? m.return : m.pnl;
                  const recorded = m.return != null;
                  const label = describeCell(cell);
                  return (
                    <td
                      key={m.month}
                      tabIndex={recorded ? 0 : undefined}
                      title={label}
                      onMouseEnter={() => onActive(recorded ? cell : null)}
                      onFocus={() => onActive(cell)}
                      onBlur={() => onActive(null)}
                      className={`h-10 rounded-md px-1 text-center font-medium outline-none focus-visible:ring-2 focus-visible:ring-accent ${
                        activeKey === cellKey(cell) ? "ring-2 ring-foreground" : ""
                      }`}
                      style={cellStyle(value, basis, thresholds)}
                    >
                      <span aria-hidden>{formatValue(value, basis)}</span>
                      <span className="sr-only">{describeCell(cell, false)}</span>
                    </td>
                  );
                })}
                <td className={`px-2 text-right font-semibold ${total > 0 ? "text-gain" : total < 0 ? "text-loss" : "text-muted"}`}>
                  {basis === "return" ? formatPercent(total, 1) : formatValue(total, "pnl")}
                  <span className="sr-only">
                    {basis === "return" ? `, 손익 ${formatSignedWon(y.total.pnl)}` : `, 수익률 ${formatPercent(y.total.return, 2)}`}
                  </span>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
