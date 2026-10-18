import { levelFor, logColor, quartileThresholds, type LogThresholds } from "@/components/log-grid/logic";
import type { MonthlyInsight } from "@/lib/api/portfolio-types";
import { formatPercent, formatSignedWon, formatWon, formatWonShort } from "@/lib/format";

// 월 칸 계산. 수익률/손익 기준에 따라 칸의 값·색·설명을 정한다.

export type Basis = "return" | "pnl";

export type MonthCell = MonthlyInsight["years"][number]["months"][number] & { year: number };

/** 수익률 경계: 1% 미만 1단계, 3% 미만 2단계, 6% 미만 3단계, 그 이상 4단계 */
export const RETURN_THRESHOLDS: LogThresholds = [0.01, 0.03, 0.06];

/** 손익 경계: 기록 있는 달 손익 |v|의 사분위수를 1만 원 단위로 맞춘 값 */
export function pnlThresholds(data: MonthlyInsight): LogThresholds {
  const values = data.years.flatMap((y) => y.months.map((m) => m.pnl)).filter((v): v is number => v != null);
  return quartileThresholds(values, (v) => Math.max(10_000, Math.round(v / 10_000) * 10_000));
}

export function cellsOf(data: MonthlyInsight): MonthCell[][] {
  return data.years.map((y) => y.months.map((m) => ({ ...m, year: y.year })));
}

export function valueOf(cell: MonthCell, basis: Basis): number | null {
  return basis === "return" ? cell.return : cell.pnl;
}

/** 칸에 보이는 짧은 값. 기록 없는 달은 "–". */
export function formatValue(value: number | null, basis: Basis): string {
  if (value == null) return "–";
  if (basis === "return") return formatPercent(value, 1);
  return `${value > 0 ? "+" : ""}${formatWonShort(value)}`;
}

/** 소수 첫째 자리에서 0.0%로 보이는 수익률은 0단계로 본다. */
const ZERO_BAND: Record<Basis, number> = { return: 0.0005, pnl: 0 };

export function cellStyle(value: number | null, basis: Basis, thresholds: LogThresholds) {
  const level = levelFor(value, thresholds, ZERO_BAND[basis]);
  // 가장 진한 단계는 배경색 글자로 대비를 맞춘다(라이트·다크 모두 4단계가 가장 대비가 크다)
  const strong = level != null && Math.abs(level) === 4;
  return {
    background: logColor(level),
    color: level == null ? "var(--muted)" : strong ? "var(--background)" : "var(--foreground)",
  };
}

/** 칸 설명(툴팁·스크린 리더). withHead가 false면 연월을 빼고 값만. */
export function describeCell(cell: MonthCell, withHead = true): string {
  const head = withHead ? `${cell.year}년 ${cell.month}월: ` : "";
  if (cell.return == null) return `${head}기록 없음`;
  const parts = [`수익률 ${formatPercent(cell.return, 2)}`, `손익 ${formatSignedWon(cell.pnl ?? 0)}`];
  if (cell.netCashFlow != null) parts.push(`순입금 ${cell.netCashFlow < 0 ? "-" : ""}${formatWon(Math.abs(cell.netCashFlow))}`);
  return `${head}${parts.join(", ")}`;
}

export function formatThresholds([a, b, c]: LogThresholds, basis: Basis): string {
  if (basis === "return") return [a, b, c].map((v) => `${+(v * 100).toFixed(1)}%`).join(" · ");
  return `${[a, b, c].map(formatWonShort).join(" · ")}원`;
}
