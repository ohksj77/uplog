import { LOG_MODE_LABELS, LOG_THRESHOLDS, logColor, type LogLevel } from "@/components/log-grid/logic";
import type { LogMode } from "@/components/log-grid/types";

const LEVELS: LogLevel[] = [-4, -3, -2, -1, 0, 1, 2, 3, 4];
const SW = 20; // 견본 너비(px)
const GAP = 2;

/** 0.005 → "0.5", 0.1 → "10" */
const pct = (t: number) => String(Math.round(t * 1000) / 10);

function describe(level: LogLevel, [a, b, c]: readonly [number, number, number]): string {
  if (level === 0) return "변동 없음";
  const side = level > 0 ? "이익" : "손실";
  const n = Math.abs(level);
  const range = n === 1 ? `${pct(a)}% 미만` : n === 2 ? `${pct(a)}–${pct(b)}%` : n === 3 ? `${pct(b)}–${pct(c)}%` : `${pct(c)}% 이상`;
  return `${side} ${range}`;
}

export type LogLegendProps = { mode: LogMode; className?: string };

/** 손실 ← 색 단계 → 이익. 현재 모드의 경계값을 함께 보여준다. */
export function LogLegend({ mode, className = "" }: LogLegendProps) {
  const t = LOG_THRESHOLDS[mode];
  // 경계가 놓이는 견본 사이 위치(견본 인덱스 k의 왼쪽 경계)와 값.
  const ticks: { k: number; text: string }[] = [
    { k: 1, text: pct(t[2]) },
    { k: 2, text: pct(t[1]) },
    { k: 3, text: pct(t[0]) },
    { k: 6, text: pct(t[0]) },
    { k: 7, text: pct(t[1]) },
    { k: 8, text: pct(t[2]) },
  ];

  return (
    <div className={`flex flex-wrap items-start gap-x-4 gap-y-2 text-[11px] text-[var(--muted)] ${className}`}>
      <div className="flex items-start gap-1.5">
        <span className="leading-3">손실</span>
        <div aria-hidden className="relative pb-3.5">
          <div className="flex" style={{ gap: GAP }}>
            {LEVELS.map((l) => (
              <span key={l} title={describe(l, t)} className="h-3 rounded-[2px]" style={{ width: SW, backgroundColor: logColor(l) }} />
            ))}
          </div>
          {ticks.map(({ k, text }) => (
            <span
              key={k}
              className="absolute top-3.5 -translate-x-1/2 text-[10px] leading-3 tabular-nums"
              style={{ left: k * (SW + GAP) - GAP / 2 }}
            >
              {text}
            </span>
          ))}
        </div>
        <span className="leading-3">이익</span>
        <span className="leading-3 opacity-80">(%)</span>
      </div>

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
        {LOG_MODE_LABELS[mode]} 색 범례: {LEVELS.filter((l) => l !== 0).map((l) => `${describe(l, t)} ${Math.abs(l)}단계`).join(", ")}, 회색은 변동 없음.
        {mode === "costBasis" ? " 매수하지 않은 날은 칠하지 않습니다." : " 가운데 점은 매수한 날입니다."}
      </p>
    </div>
  );
}
