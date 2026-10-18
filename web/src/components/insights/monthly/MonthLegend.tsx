import { formatThresholds, type Basis } from "@/components/insights/monthly/month-cell";
import { logColor, type LogLevel, type LogThresholds } from "@/components/log-grid/logic";

// 칸 색 범례. 손실 진함 → 0 → 이익 진함, 그리고 기록 없음.

const LEVELS: LogLevel[] = [-4, -3, -2, -1, 0, 1, 2, 3, 4];

export function MonthLegend({ basis, thresholds }: { basis: Basis; thresholds: LogThresholds }) {
  const bounds = formatThresholds(thresholds, basis);
  const what = basis === "return" ? "수익률" : "손익";
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-xs text-muted">
      <div
        className="flex items-center gap-1.5"
        role="img"
        aria-label={`칸 색은 그달 ${what}. 빨강은 이익, 파랑은 손실이고 진할수록 커요. 경계는 ${bounds}.`}
      >
        <span aria-hidden>손실</span>
        <span className="flex gap-0.5" aria-hidden>
          {LEVELS.map((l) => (
            <span key={l} className="h-3 w-3 rounded-[3px]" style={{ background: logColor(l) }} />
          ))}
        </span>
        <span aria-hidden>이익</span>
      </div>
      <span aria-hidden>경계 {bounds}</span>
      <span className="flex items-center gap-1.5" aria-hidden>
        <span className="h-3 w-3 rounded-[3px]" style={{ background: logColor(null) }} /> 기록 없음
      </span>
    </div>
  );
}
