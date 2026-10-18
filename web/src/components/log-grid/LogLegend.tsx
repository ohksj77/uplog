import { LOG_MODE_LABELS, LOG_THRESHOLDS, logColor, type LogLevel } from "@/components/log-grid/logic";
import { LogScale, describeLevel } from "@/components/log-grid/LogScale";
import type { LogMode } from "@/components/log-grid/types";

const LEVELS: LogLevel[] = [-4, -3, -2, -1, 1, 2, 3, 4];

/** 0.005 → "0.5", 0.1 → "10" */
const pct = (t: number) => String(Math.round(t * 1000) / 10);

export type LogLegendProps = { mode: LogMode; className?: string };

/** 손실 ← 색 단계 → 이익. 현재 모드의 경계값을 함께 보여준다. */
export function LogLegend({ mode, className = "" }: LogLegendProps) {
  const t = LOG_THRESHOLDS[mode];
  return (
    <div className={`flex flex-wrap items-start gap-x-4 gap-y-2 text-[11px] text-[var(--muted)] ${className}`}>
      <LogScale thresholds={t} format={pct} unit="(%)" suffix="%" />

      {mode === "costBasis" ? (
        <span className="flex items-center gap-1.5 leading-3">
          <span aria-hidden className="size-3 rounded-[2px]" style={{ backgroundColor: logColor(null) }} />
          매수 안 한 날
        </span>
      ) : (
        <span className="flex items-center gap-1.5 leading-3">
          <span aria-hidden className="relative size-3 rounded-[2px]" style={{ backgroundColor: logColor(0) }}>
            <span
              className="absolute top-1/2 left-1/2 size-1 -translate-x-1/2 -translate-y-1/2 rounded-full bg-[var(--foreground)]"
              style={{ boxShadow: "0 0 0 1px var(--background)" }}
            />
          </span>
          매수일
        </span>
      )}

      <p className="sr-only">
        {LOG_MODE_LABELS[mode]} 색 범례: {LEVELS.map((l) => `${describeLevel(l, t, pct, "%")} ${Math.abs(l)}단계`).join(", ")}, 회색은 변동 없음.
        {mode === "costBasis" ? " 매수하지 않은 날은 칠하지 않습니다." : " 가운데 점은 매수한 날입니다."}
      </p>
    </div>
  );
}
