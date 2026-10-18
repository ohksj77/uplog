"use client";

import { useId, useState, type CSSProperties, type KeyboardEvent, type MouseEvent } from "react";
import { linearScale, spreadIndexes } from "@/components/charts/scale";
import { useElementWidth } from "@/components/charts/use-element-width";

// 손으로 그린 SVG 선 차트. 시계열 여러 개를 한 축(y 하나)에 겹친다. 기능 B에서도 재사용한다.

export type LineSeries = {
  id: string;
  label: string;
  /** labels와 같은 길이. null은 값 없음(선이 끊김). */
  values: (number | null)[];
  /** 글자색 클래스. 선·점·범례가 모두 currentColor를 쓴다. 예: "text-[#7c4dff]" */
  colorClassName: string;
  dashed?: boolean;
  /** 계단형(값이 바뀌는 시점에 수직으로 꺾임). 투입 원금처럼 띄엄띄엄 바뀌는 값용. */
  step?: boolean;
};

export type LineChartProps = {
  /** x축 값(보통 날짜). 오름차순. */
  labels: string[];
  series: LineSeries[];
  formatY: (value: number) => string;
  formatX?: (label: string) => string;
  formatTooltipTitle?: (label: string) => string;
  formatTooltipValue?: (value: number) => string;
  /** 밖에서 고정한 지점(예: 로그에서 고른 날). 마우스를 올리지 않았을 때 보여준다. */
  activeIndex?: number | null;
  /** 차트를 누르거나 Enter를 눌렀을 때. */
  onSelectIndex?: (index: number) => void;
  /** 스크린리더용 차트 이름 */
  title: string;
  /** 스크린리더용 텍스트 요약 */
  description?: string;
  height?: number;
};

const MARGIN = { top: 12, right: 12, bottom: 28, left: 52 };

export function LineChart({
  labels,
  series,
  formatY,
  formatX = (l) => l,
  formatTooltipTitle = formatX,
  formatTooltipValue = formatY,
  activeIndex = null,
  onSelectIndex,
  title,
  description,
  height = 240,
}: LineChartProps) {
  const [ref, width] = useElementWidth<HTMLDivElement>();
  const [hoverIndex, setHoverIndex] = useState<number | null>(null);
  const [keyIndex, setKeyIndex] = useState<number | null>(null);
  const descId = useId();

  const n = labels.length;
  const innerW = Math.max(0, width - MARGIN.left - MARGIN.right);
  const innerH = height - MARGIN.top - MARGIN.bottom;

  const all = series.flatMap((s) => s.values.filter((v): v is number => v != null));
  const y = linearScale(all.length ? Math.min(...all) : 0, all.length ? Math.max(...all) : 1, [
    MARGIN.top + innerH,
    MARGIN.top,
  ]);
  const x = (i: number) => MARGIN.left + (n <= 1 ? innerW / 2 : (i / (n - 1)) * innerW);

  const shown = hoverIndex ?? keyIndex ?? activeIndex;
  const clamp = (i: number) => Math.min(n - 1, Math.max(0, i));

  const indexAt = (e: MouseEvent<SVGSVGElement>) => {
    const rect = e.currentTarget.getBoundingClientRect();
    const px = e.clientX - rect.left - MARGIN.left;
    return clamp(Math.round(n <= 1 ? 0 : (px / innerW) * (n - 1)));
  };

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const base = keyIndex ?? activeIndex ?? n - 1;
    const stepBig = Math.max(1, Math.round(n / 20));
    const moves: Record<string, number> = {
      ArrowLeft: base - 1,
      ArrowRight: base + 1,
      PageUp: base - stepBig,
      PageDown: base + stepBig,
      Home: 0,
      End: n - 1,
    };
    if (e.key in moves) {
      e.preventDefault();
      setKeyIndex(clamp(moves[e.key]));
    } else if ((e.key === "Enter" || e.key === " ") && keyIndex != null) {
      e.preventDefault();
      onSelectIndex?.(keyIndex);
    } else if (e.key === "Escape") {
      setKeyIndex(null);
    }
  };

  const xTicks = spreadIndexes(n, width < 480 ? 3 : 5);
  const tooltipRight = shown != null && x(shown) > width / 2;

  return (
    <figure className="m-0">
      <ul className="mb-3 flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted" aria-hidden>
        {series.map((s) => (
          <li key={s.id} className="flex items-center gap-1.5">
            <span className={`${s.colorClassName} inline-block w-4 border-t-2 border-current ${s.dashed ? "border-dashed" : ""}`} />
            {s.label}
          </li>
        ))}
      </ul>

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
            onClick={(e) => onSelectIndex?.(indexAt(e))}
          >
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

            {series.map((s) => (
              <path
                key={s.id}
                className={s.colorClassName}
                d={linePath(s.values, x, y, s.step)}
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                strokeLinejoin="round"
                strokeLinecap="round"
                strokeDasharray={s.dashed ? "4 4" : undefined}
              />
            ))}

            {shown != null && (
              <g>
                <line
                  x1={x(shown)}
                  x2={x(shown)}
                  y1={MARGIN.top}
                  y2={MARGIN.top + innerH}
                  stroke="var(--muted)"
                  strokeWidth={1}
                />
                {series.map((s) => {
                  const v = s.values[shown];
                  return v == null ? null : (
                    <circle
                      key={s.id}
                      className={s.colorClassName}
                      cx={x(shown)}
                      cy={y(v)}
                      r={4}
                      fill="currentColor"
                      stroke="var(--background)"
                      strokeWidth={2}
                    />
                  );
                })}
              </g>
            )}
          </svg>
        )}

        {shown != null && width > 0 && (
          <div
            aria-hidden
            className="pointer-events-none absolute top-2 z-10 min-w-36 rounded-xl border border-border bg-background px-3 py-2 text-xs shadow-lg"
            style={tooltipStyle(x(shown), tooltipRight, width)}
          >
            <TooltipBody
              title={formatTooltipTitle(labels[shown])}
              rows={series.map((s) => ({ s, v: s.values[shown] }))}
              format={formatTooltipValue}
            />
          </div>
        )}
      </div>

      {description && (
        <figcaption id={descId} className="sr-only">
          {description}
        </figcaption>
      )}
      {/* 키보드로 옮길 때만 읽어 준다 */}
      <p className="sr-only" aria-live="polite">
        {keyIndex != null &&
          `${formatTooltipTitle(labels[keyIndex])}: ${series
            .map((s) => `${s.label} ${s.values[keyIndex] == null ? "없음" : formatTooltipValue(s.values[keyIndex]!)}`)
            .join(", ")}`}
      </p>
    </figure>
  );
}

function TooltipBody({
  title,
  rows,
  format,
}: {
  title: string;
  rows: { s: LineSeries; v: number | null }[];
  format: (v: number) => string;
}) {
  return (
    <>
      <p className="mb-1 font-medium text-foreground">{title}</p>
      <dl className="space-y-0.5">
        {rows.map(({ s, v }) => (
          <div key={s.id} className="flex items-center justify-between gap-3">
            <dt className="flex items-center gap-1.5 text-muted">
              <span className={`${s.colorClassName} inline-block w-3 border-t-2 border-current ${s.dashed ? "border-dashed" : ""}`} />
              {s.label}
            </dt>
            <dd className="font-medium text-foreground tabular-nums">{v == null ? "–" : format(v)}</dd>
          </div>
        ))}
      </dl>
    </>
  );
}

// 좁은 화면에서는 툴팁이 차트 밖으로 넘치지 않게 십자선 반대편 모서리에 붙인다.
function tooltipStyle(cx: number, onRight: boolean, width: number): CSSProperties {
  if (width < 560) return onRight ? { left: MARGIN.left } : { right: 0 };
  return { left: cx, transform: onRight ? "translateX(calc(-100% - 12px))" : "translateX(12px)" };
}

function linePath(
  values: (number | null)[],
  x: (i: number) => number,
  y: (v: number) => number,
  step = false,
): string {
  let d = "";
  let prev: number | null = null;
  values.forEach((v, i) => {
    if (v == null) {
      prev = null;
      return;
    }
    const px = x(i).toFixed(1);
    const py = y(v).toFixed(1);
    if (prev == null) d += `M${px},${py}`;
    else if (step) d += `H${px}V${py}`;
    else d += `L${px},${py}`;
    prev = v;
  });
  return d;
}
