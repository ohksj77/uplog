"use client";

import { useId, useState, type KeyboardEvent, type PointerEvent } from "react";
import { linearScale, spreadIndexes } from "@/components/charts/scale";
import { useElementWidth } from "@/components/charts/use-element-width";

// 0을 기준으로 위는 빨강(이익), 아래는 파랑(손실)으로 채우는 면적 차트. 수중 차트와 그림자 계좌 차이에 쓴다.

export type SignedAreaChartProps = {
  /** x축 값(날짜). 오름차순. */
  labels: string[];
  values: number[];
  /** 툴팁·스크린리더에서 값 앞에 붙는 이름 */
  valueLabel: string;
  formatY: (value: number) => string;
  formatX?: (label: string) => string;
  formatTooltipTitle?: (label: string) => string;
  formatTooltipValue?: (value: number) => string;
  /** 강조할 구간(인덱스, 양끝 포함). 예: 지금 진행 중인 하락. */
  highlight?: { from: number; to: number; label: string } | null;
  /** 가로 기준선. 예: 과거 가장 깊었던 하락. */
  reference?: { value: number; label: string } | null;
  title: string;
  description?: string;
  height?: number;
};

const MARGIN = { top: 12, right: 12, bottom: 28, left: 52 };

export function SignedAreaChart({
  labels,
  values,
  valueLabel,
  formatY,
  formatX = (l) => l,
  formatTooltipTitle = formatX,
  formatTooltipValue = formatY,
  highlight = null,
  reference = null,
  title,
  description,
  height = 220,
}: SignedAreaChartProps) {
  const [ref, width] = useElementWidth<HTMLDivElement>();
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);
  const [keyIndex, setKeyIndex] = useState<number | null>(null);
  const uid = useId().replace(/[^a-zA-Z0-9_-]/g, "");
  const descId = `${uid}-desc`;
  const clipUp = `${uid}-up`;
  const clipDown = `${uid}-down`;

  const n = labels.length;
  const innerW = Math.max(0, width - MARGIN.left - MARGIN.right);
  const innerH = height - MARGIN.top - MARGIN.bottom;
  const bottom = MARGIN.top + innerH;

  const extent = [...values, 0, ...(reference ? [reference.value] : [])];
  const y = linearScale(Math.min(...extent), Math.max(...extent), [bottom, MARGIN.top]);
  const x = (i: number) => MARGIN.left + (n <= 1 ? innerW / 2 : (i / (n - 1)) * innerW);
  const y0 = y(0);

  const shown = hoverIndex ?? keyIndex;
  const clamp = (i: number) => Math.min(n - 1, Math.max(0, i));

  const indexAt = (e: PointerEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const px = e.clientX - rect.left - MARGIN.left;
    return clamp(Math.round(n <= 1 ? 0 : (px / innerW) * (n - 1)));
  };

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const base = keyIndex ?? n - 1;
    const big = Math.max(1, Math.round(n / 20));
    const moves: Record<string, number> = {
      ArrowLeft: base - 1,
      ArrowRight: base + 1,
      PageUp: base - big,
      PageDown: base + big,
      Home: 0,
      End: n - 1,
    };
    if (e.key in moves) {
      e.preventDefault();
      setKeyIndex(clamp(moves[e.key]));
    } else if (e.key === "Escape") {
      setKeyIndex(null);
    }
  };

  const line = values.map((v, i) => `${i === 0 ? "M" : "L"}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join("");
  const area = n > 0 ? `${line}L${x(n - 1).toFixed(1)},${y0.toFixed(1)}L${x(0).toFixed(1)},${y0.toFixed(1)}Z` : "";
  const xTicks = spreadIndexes(n, width < 480 ? 3 : 5);
  const tooltipRight = shown != null && x(shown) > width / 2;

  return (
    <figure className="m-0">
      <div
        ref={ref}
        className="relative w-full rounded-lg outline-none focus-visible:ring-2 focus-visible:ring-accent"
        style={{ height }}
        tabIndex={n > 0 ? 0 : -1}
        role="group"
        aria-label={`${title}. 좌우 화살표로 날짜를 옮길 수 있어요.`}
        aria-describedby={description ? descId : undefined}
        onKeyDown={onKeyDown}
        onBlur={() => setKeyIndex(null)}
      >
        {width > 0 && n > 0 && (
          <svg
            width={width}
            height={height}
            className="block select-none"
            style={{ touchAction: "pan-y" }}
            aria-hidden
            onPointerMove={(e) => setHoverIndex(indexAt(e))}
            onPointerLeave={() => setHoverIndex(null)}
          >
            <defs>
              <clipPath id={clipUp}>
                <rect x={0} y={0} width={width} height={Math.max(0, y0)} />
              </clipPath>
              <clipPath id={clipDown}>
                <rect x={0} y={y0} width={width} height={Math.max(0, height - y0)} />
              </clipPath>
            </defs>

            {highlight && (
              <g>
                <rect
                  x={x(highlight.from)}
                  y={MARGIN.top}
                  width={Math.max(2, x(highlight.to) - x(highlight.from))}
                  height={innerH}
                  fill="var(--muted)"
                  opacity={0.12}
                />
                <text
                  x={x(highlight.to)}
                  y={bottom - 6}
                  textAnchor="end"
                  fontSize={11}
                  fill="var(--foreground)"
                  fontWeight={600}
                >
                  {highlight.label}
                </text>
              </g>
            )}

            {y.ticks.map((t) => (
              <g key={t}>
                <line
                  x1={MARGIN.left}
                  x2={width - MARGIN.right}
                  y1={y(t)}
                  y2={y(t)}
                  stroke="var(--border)"
                  strokeWidth={1}
                />
                <text x={MARGIN.left - 8} y={y(t)} dy="0.32em" textAnchor="end" fontSize={11} fill="var(--muted)">
                  {formatY(t)}
                </text>
              </g>
            ))}

            {xTicks.map((i, k) => (
              <text
                key={i}
                x={x(i)}
                y={height - 8}
                fontSize={11}
                fill="var(--muted)"
                textAnchor={k === 0 ? "start" : k === xTicks.length - 1 ? "end" : "middle"}
              >
                {formatX(labels[i])}
              </text>
            ))}

            <g clipPath={`url(#${clipUp})`}>
              <path d={area} fill="var(--gain)" opacity={0.22} />
              <path d={line} fill="none" stroke="var(--gain)" strokeWidth={1.5} strokeLinejoin="round" />
            </g>
            <g clipPath={`url(#${clipDown})`}>
              <path d={area} fill="var(--loss)" opacity={0.22} />
              <path d={line} fill="none" stroke="var(--loss)" strokeWidth={1.5} strokeLinejoin="round" />
            </g>
            <line x1={MARGIN.left} x2={width - MARGIN.right} y1={y0} y2={y0} stroke="var(--muted)" strokeWidth={1} />

            {reference && (
              <g>
                <line
                  x1={MARGIN.left}
                  x2={width - MARGIN.right}
                  y1={y(reference.value)}
                  y2={y(reference.value)}
                  stroke="var(--foreground)"
                  strokeWidth={1}
                  strokeDasharray="4 4"
                />
                <text
                  x={MARGIN.left + 4}
                  y={y(reference.value)}
                  dy="-0.4em"
                  fontSize={11}
                  fill="var(--foreground)"
                >
                  {reference.label}
                </text>
              </g>
            )}

            {shown != null && (
              <g>
                <line x1={x(shown)} x2={x(shown)} y1={MARGIN.top} y2={bottom} stroke="var(--muted)" strokeWidth={1} />
                <circle
                  cx={x(shown)}
                  cy={y(values[shown])}
                  r={4}
                  fill={values[shown] < 0 ? "var(--loss)" : values[shown] > 0 ? "var(--gain)" : "var(--muted)"}
                  stroke="var(--background)"
                  strokeWidth={2}
                />
              </g>
            )}
          </svg>
        )}

        {shown != null && width > 0 && (
          <div
            aria-hidden
            className="pointer-events-none absolute top-2 z-10 rounded-xl border border-border bg-background px-3 py-2 text-xs shadow-lg"
            style={
              width < 560
                ? tooltipRight
                  ? { left: MARGIN.left }
                  : { right: 0 }
                : { left: x(shown), transform: tooltipRight ? "translateX(calc(-100% - 12px))" : "translateX(12px)" }
            }
          >
            <p className="mb-1 font-medium text-foreground">{formatTooltipTitle(labels[shown])}</p>
            <p className="flex justify-between gap-3">
              <span className="text-muted">{valueLabel}</span>
              <span className="font-medium text-foreground tabular-nums">{formatTooltipValue(values[shown])}</span>
            </p>
          </div>
        )}
      </div>

      {description && (
        <figcaption id={descId} className="sr-only">
          {description}
        </figcaption>
      )}
      <p className="sr-only" aria-live="polite">
        {keyIndex != null && `${formatTooltipTitle(labels[keyIndex])}: ${valueLabel} ${formatTooltipValue(values[keyIndex])}`}
      </p>
    </figure>
  );
}
