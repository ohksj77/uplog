import { logColor, type LogLevel, type LogThresholds } from "@/components/log-grid/logic";

const LEVELS: LogLevel[] = [-4, -3, -2, -1, 0, 1, 2, 3, 4];
const GAP = 2;

export type LogScaleProps = {
  thresholds: LogThresholds;
  /** 경계값 → 눈금 문구(예: 0.005 → "0.5") */
  format: (t: number) => string;
  /** 막대 옆 단위 표시, 예: "(%)" */
  unit?: string;
  /** 단계 설명(title)에서 값 뒤에 붙이는 단위, 예: "%" */
  suffix?: string;
  /** 견본 너비(px). 눈금 문구가 길면 넓힌다. */
  swatch?: number;
};

/** 경계 [a, b, c]의 단계 설명. */
export function describeLevel(level: LogLevel, [a, b, c]: LogThresholds, format: (t: number) => string, unit = ""): string {
  if (level === 0) return "변동 없음";
  const side = level > 0 ? "이익" : "손실";
  const n = Math.abs(level);
  const f = format;
  const range =
    n === 1 ? `${f(a)}${unit} 미만` : n === 2 ? `${f(a)}–${f(b)}${unit}` : n === 3 ? `${f(b)}–${f(c)}${unit}` : `${f(c)}${unit} 이상`;
  return `${side} ${range}`;
}

/** 손실 ← 색 단계 → 이익 막대와 경계 눈금. 시각용(aria-hidden) — 설명은 부르는 쪽이 sr-only로 붙인다. */
export function LogScale({ thresholds: t, format, unit, suffix = "", swatch = 20 }: LogScaleProps) {
  // 경계가 놓이는 견본 사이 위치(견본 인덱스 k의 왼쪽 경계)와 값.
  const ticks: { k: number; text: string }[] = [
    { k: 1, text: format(t[2]) },
    { k: 2, text: format(t[1]) },
    { k: 3, text: format(t[0]) },
    { k: 6, text: format(t[0]) },
    { k: 7, text: format(t[1]) },
    { k: 8, text: format(t[2]) },
  ];

  return (
    <div className="flex items-start gap-1.5">
      <span className="leading-3">손실</span>
      <div aria-hidden className="relative pb-3.5">
        <div className="flex" style={{ gap: GAP }}>
          {LEVELS.map((l) => (
            <span
              key={l}
              title={describeLevel(l, t, format, suffix)}
              className="h-3 rounded-[2px]"
              style={{ width: swatch, backgroundColor: logColor(l) }}
            />
          ))}
        </div>
        {ticks.map(({ k, text }) => (
          <span
            key={k}
            className="absolute top-3.5 -translate-x-1/2 text-[10px] leading-3 whitespace-nowrap tabular-nums"
            style={{ left: k * (swatch + GAP) - GAP / 2 }}
          >
            {text}
          </span>
        ))}
      </div>
      <span className="leading-3">이익</span>
      {unit && <span className="leading-3 opacity-80">{unit}</span>}
    </div>
  );
}
