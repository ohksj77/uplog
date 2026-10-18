"use client";

import { useState, type PointerEvent } from "react";
import { linearScale, spreadIndexes } from "@/components/charts/scale";
import { useElementWidth } from "@/components/charts/use-element-width";
import { formatDateShort } from "@/components/holding/format";
import type { PricePoint } from "@/lib/api/portfolio-types";

// 주가 선 차트. 보유 중인(또는 매도 전) 구간은 실선, 그 뒤는 점선으로 이어 그린다.
// 판 뒤의 그림자(작은 차트)와 종목 상세(큰 차트)가 함께 쓴다.

export type TrailMarker = { index: number; side: "BUY" | "SELL" };
/** 매도가 같은 기준선. fromIndex~toIndex 구간에 가로로 긋는다. */
export type TrailRef = { fromIndex: number; toIndex: number; value: number };

export type TrailChartProps = {
  points: PricePoint[];
  /** solid[i]: i → i+1 구간을 실선으로 그릴지 */
  solid: boolean[];
  markers?: TrailMarker[];
  refs?: TrailRef[];
  /** 세로 안내선(예: 매도 N거래일 뒤) */
  guides?: number[];
  formatY: (value: number) => string;
  /** 스크린리더용 이름과 요약 */
  title: string;
  description: string;
  height?: number;
  /** 작은 차트: y축 눈금 없이 처음·끝 날짜만 */
  compact?: boolean;
  /** 마우스를 올린 날의 추가 설명(예: 그날 체결) */
  describeIndex?: (index: number) => string | null;
};

/** 0..until-1 구간만 실선 */
export function solidUntil(length: number, until: number): boolean[] {
  return Array.from({ length }, (_, i) => i < until);
}

export function TrailChart({
  points,
  solid,
  markers = [],
  refs = [],
  guides = [],
  formatY,
  title,
  description,
  height = 220,
  compact = false,
  describeIndex,
}: TrailChartProps) {
  const [ref, width] = useElementWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);
  const margin = compact ? { top: 10, right: 6, bottom: 18, left: 6 } : { top: 12, right: 12, bottom: 26, left: 60 };

  const n = points.length;
  const innerW = Math.max(0, width - margin.left - margin.right);
  const innerH = height - margin.top - margin.bottom;
  const values = [...points.map((p) => p.closeLocal), ...refs.map((r) => r.value)];
  const y = linearScale(Math.min(...values), Math.max(...values), [margin.top + innerH, margin.top], compact ? 2 : 4);
  const x = (i: number) => margin.left + (n <= 1 ? innerW / 2 : (i / (n - 1)) * innerW);
  const px = (i: number) => x(i).toFixed(1);
  const py = (v: number) => y(v).toFixed(1);

  const [solidD, dashedD] = splitPaths(points, solid, px, py);
  const xTicks = compact ? (n > 1 ? [0, n - 1] : [0]) : spreadIndexes(n, width < 480 ? 3 : 5);

  const onMove = (e: PointerEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const i = Math.round(((e.clientX - rect.left - margin.left) / Math.max(1, innerW)) * (n - 1));
    setHover(Math.min(n - 1, Math.max(0, i)));
  };

  const hovered = !compact && hover != null ? points[hover] : null;
  const extra = hovered && describeIndex ? describeIndex(hover!) : null;

  return (
    <figure className="m-0" role="img" aria-label={`${title}. ${description}`}>
      <div ref={ref} className="relative w-full" style={{ height }}>
        {width > 0 && n > 0 && (
          <svg
            width={width}
            height={height}
            className="block select-none"
            style={{ touchAction: "pan-y" }}
            aria-hidden
            onPointerMove={compact ? undefined : onMove}
            onPointerLeave={compact ? undefined : () => setHover(null)}
          >
            {!compact &&
              y.ticks.map((t) => (
                <g key={t}>
                  <line x1={margin.left} x2={width - margin.right} y1={y(t)} y2={y(t)} stroke="var(--border)" />
                  <text x={margin.left - 8} y={y(t)} dy="0.32em" textAnchor="end" fontSize={11} fill="var(--muted)">
                    {formatY(t)}
                  </text>
                </g>
              ))}
            {xTicks.map((i, k) => (
              <text
                key={i}
                x={x(i)}
                y={height - (compact ? 4 : 8)}
                fontSize={compact ? 10 : 11}
                fill="var(--muted)"
                textAnchor={k === 0 ? "start" : k === xTicks.length - 1 ? "end" : "middle"}
              >
                {formatDateShort(points[i].date)}
              </text>
            ))}

            {guides
              .filter((g) => g >= 0 && g < n)
              .map((g) => (
                <line key={g} x1={x(g)} x2={x(g)} y1={margin.top} y2={margin.top + innerH} stroke="var(--border)" strokeWidth={1.5} />
              ))}

            {refs.map((r, k) => (
              <line
                key={k}
                className="text-trade-sell"
                x1={x(r.fromIndex)}
                x2={x(Math.min(n - 1, r.toIndex))}
                y1={y(r.value)}
                y2={y(r.value)}
                stroke="currentColor"
                strokeWidth={1}
                strokeDasharray="2 3"
              />
            ))}

            <path d={dashedD} className="text-muted" fill="none" stroke="currentColor" strokeWidth={1.5} strokeDasharray="4 3" />
            <path d={solidD} className="text-foreground" fill="none" stroke="currentColor" strokeWidth={compact ? 1.5 : 2} strokeLinejoin="round" />

            {markers
              .filter((m) => m.index >= 0 && m.index < n)
              .map((m, k) => (
                <Marker key={k} cx={x(m.index)} cy={y(points[m.index].closeLocal)} side={m.side} />
              ))}

            {hover != null && hovered && (
              <g>
                <line x1={x(hover)} x2={x(hover)} y1={margin.top} y2={margin.top + innerH} stroke="var(--muted)" />
                <circle cx={x(hover)} cy={y(hovered.closeLocal)} r={3.5} fill="var(--foreground)" stroke="var(--background)" strokeWidth={2} />
              </g>
            )}
          </svg>
        )}
        {hovered && (
          <div
            aria-hidden
            className="pointer-events-none absolute top-0 right-0 rounded-lg border border-border bg-background px-2 py-1 text-xs shadow-sm"
          >
            <span className="text-muted">{formatDateShort(hovered.date)}</span>{" "}
            <span className="font-medium tabular-nums">{formatY(hovered.closeLocal)}</span>
            {extra && <span className="ml-1 text-muted">· {extra}</span>}
          </div>
        )}
      </div>
    </figure>
  );
}

function Marker({ cx, cy, side }: { cx: number; cy: number; side: "BUY" | "SELL" }) {
  // 매수는 점 아래 ▲(청록), 매도는 점 위 ▼(호박색)
  const s = 5;
  const d =
    side === "BUY"
      ? `M${cx},${cy + 4} l${s},${s * 1.6} h${-2 * s} z`
      : `M${cx},${cy - 4} l${s},${-s * 1.6} h${-2 * s} z`;
  return (
    <g className={side === "BUY" ? "text-trade-buy" : "text-trade-sell"}>
      <circle cx={cx} cy={cy} r={3} fill="currentColor" stroke="var(--background)" strokeWidth={1.5} />
      <path d={d} fill="currentColor" />
    </g>
  );
}

/** 구간마다 실선/점선 경로로 나눈다. */
function splitPaths(
  points: PricePoint[],
  solid: boolean[],
  px: (i: number) => string,
  py: (v: number) => string,
): [string, string] {
  let a = "";
  let b = "";
  let last: boolean | null = null;
  for (let i = 0; i < points.length - 1; i++) {
    const isSolid = solid[i] ?? false;
    const seg = `L${px(i + 1)},${py(points[i + 1].closeLocal)}`;
    const start = last === isSolid ? "" : `M${px(i)},${py(points[i].closeLocal)}`;
    if (isSolid) a += start + seg;
    else b += start + seg;
    last = isSolid;
  }
  return [a, b];
}
