"use client";

import { useId, useState, type KeyboardEvent, type PointerEvent } from "react";
import { linearScale, spreadIndexes } from "@/components/charts/scale";
import { useElementWidth } from "@/components/charts/use-element-width";

// 쌓은 영역 차트. 시리즈를 아래에서 위로 쌓고, 띠 사이엔 배경색 선을 둬서 경계를 보인다.

export type StackedSeries = {
  key: string;
  label: string;
  /** CSS 색(토큰 변수), 예: "var(--cat-1)" */
  color: string;
  values: number[];
};

type StackedAreaChartProps = {
  labels: string[];
  series: StackedSeries[];
  formatY: (value: number) => string;
  formatX: (label: string) => string;
  /** 툴팁 한 줄: 값과 비중 */
  formatValue: (value: number, share: number) => string;
  formatTitle?: (label: string) => string;
  title: string;
  description: string;
  height?: number;
};

const MARGIN = { top: 12, right: 12, bottom: 28, left: 56 };

export function StackedAreaChart({
  labels,
  series,
  formatY,
  formatX,
  formatValue,
  formatTitle = formatX,
  title,
  description,
  height = 260,
}: StackedAreaChartProps) {
  const [ref, width] = useElementWidth<HTMLDivElement>();
  const [active, setActive] = useState<number | null>(null);
  const descId = useId();
  const n = labels.length;
  const innerW = Math.max(0, width - MARGIN.left - MARGIN.right);
  const innerH = height - MARGIN.top - MARGIN.bottom;

  const totals = labels.map((_, i) => series.reduce((s, x) => s + Math.max(0, x.values[i] ?? 0), 0));
  const y = linearScale(0, Math.max(1, ...totals), [MARGIN.top + innerH, MARGIN.top]);
  const x = (i: number) => MARGIN.left + (n <= 1 ? innerW / 2 : (i / (n - 1)) * innerW);

  // 아래에서부터 누적한 경계
  const lower: number[][] = [];
  const upper: number[][] = [];
  const acc = labels.map(() => 0);
  for (const s of series) {
    lower.push([...acc]);
    s.values.forEach((v, i) => (acc[i] += Math.max(0, v ?? 0)));
    upper.push([...acc]);
  }
  const area = (k: number) => {
    if (n === 0) return "";
    const top = upper[k].map((v, i) => `${i === 0 ? "M" : "L"}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join("");
    const bottom = lower[k]
      .map((v, i) => [i, v] as const)
      .reverse()
      .map(([i, v]) => `L${x(i).toFixed(1)},${y(v).toFixed(1)}`)
      .join("");
    return `${top}${bottom}Z`;
  };

  const clamp = (i: number) => Math.min(n - 1, Math.max(0, i));
  const onPointer = (e: PointerEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const px = e.clientX - rect.left - MARGIN.left;
    setActive(clamp(Math.round(n <= 1 ? 0 : (px / Math.max(1, innerW)) * (n - 1))));
  };
  const onKey = (e: KeyboardEvent<HTMLDivElement>) => {
    const base = active ?? n - 1;
    const moves: Record<string, number> = { ArrowLeft: base - 1, ArrowRight: base + 1, Home: 0, End: n - 1 };
    if (e.key in moves) {
      e.preventDefault();
      setActive(clamp(moves[e.key]));
    } else if (e.key === "Escape") {
      setActive(null);
    }
  };

  const xTicks = spreadIndexes(n, width < 480 ? 3 : 5);
  const shown = active;
  const tipRight = shown != null && x(shown) > width / 2;

  return (
    <figure className="m-0">
      <div
        ref={ref}
        className="relative w-full rounded-lg outline-none focus-visible:ring-2 focus-visible:ring-accent"
        style={{ height }}
        tabIndex={n > 0 ? 0 : -1}
        role="group"
        aria-label={title}
        aria-describedby={descId}
        onKeyDown={onKey}
        onBlur={() => setActive(null)}
      >
        {width > 0 && (
          <svg width={width} height={height} onPointerMove={onPointer} onPointerLeave={() => setActive(null)} aria-hidden>
            {y.ticks.map((t) => (
              <g key={t}>
                <line x1={MARGIN.left} x2={width - MARGIN.right} y1={y(t)} y2={y(t)} stroke="var(--border)" strokeWidth={1} />
                <text x={MARGIN.left - 8} y={y(t)} textAnchor="end" dominantBaseline="middle" fontSize={11} fill="var(--muted)">
                  {formatY(t)}
                </text>
              </g>
            ))}
            {series.map((s, k) => (
              <path key={s.key} d={area(k)} fill={s.color} stroke="var(--background)" strokeWidth={1} strokeLinejoin="round" />
            ))}
            {xTicks.map((i) => (
              <text
                key={i}
                x={x(i)}
                y={height - 8}
                textAnchor={i === 0 ? "start" : i === n - 1 ? "end" : "middle"}
                fontSize={11}
                fill="var(--muted)"
              >
                {formatX(labels[i])}
              </text>
            ))}
            {shown != null && (
              <line x1={x(shown)} x2={x(shown)} y1={MARGIN.top} y2={MARGIN.top + innerH} stroke="var(--foreground)" strokeWidth={1} />
            )}
          </svg>
        )}
        {shown != null && (
          <div
            className="pointer-events-none absolute top-2 z-10 w-max max-w-[240px] rounded-md bg-[var(--foreground)] px-3 py-2 text-xs leading-5 text-[var(--background)] shadow-lg"
            style={
              // 좁은 화면에선 크로스헤어 반대쪽 모서리에 붙여 화면 밖으로 나가지 않게 한다
              width < 560
                ? tipRight
                  ? { left: MARGIN.left }
                  : { right: MARGIN.right }
                : tipRight
                  ? { right: width - x(shown) + 8 }
                  : { left: x(shown) + 8 }
            }
          >
            <p className="font-semibold">{formatTitle(labels[shown])}</p>
            {[...series].reverse().map((s) => {
              const v = Math.max(0, s.values[shown] ?? 0);
              if (v <= 0) return null;
              return (
                <p key={s.key} className="flex items-center gap-1.5 tabular-nums">
                  <span aria-hidden className="inline-block size-2 shrink-0 rounded-sm" style={{ background: s.color }} />
                  <span className="truncate">{s.label}</span>
                  <span className="ml-auto pl-2">{formatValue(v, totals[shown] > 0 ? v / totals[shown] : 0)}</span>
                </p>
              );
            })}
          </div>
        )}
        <p aria-live="polite" className="sr-only">
          {shown != null
            ? `${formatTitle(labels[shown])}: ` +
              series
                .filter((s) => (s.values[shown] ?? 0) > 0)
                .map((s) => `${s.label} ${formatValue(s.values[shown], totals[shown] > 0 ? s.values[shown] / totals[shown] : 0)}`)
                .join(", ")
            : ""}
        </p>
      </div>
      <figcaption id={descId} className="sr-only">
        {description}
      </figcaption>
    </figure>
  );
}
