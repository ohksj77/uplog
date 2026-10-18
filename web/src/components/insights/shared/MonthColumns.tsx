"use client";

import { useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { formatManShort } from "@/components/insights/shared/money";

// 월별 세로 막대(쌓기 가능). 배당·비용 화면이 함께 쓴다.
// 막대 영역만 가로로 스크롤하고 처음엔 가장 최근 달이 보이게 한다. 값이 0인 달은 바닥선만 남는다.
// 막대는 Tab으로 옮겨 다니고, 마우스를 올리거나 누르거나 포커스하면 아래 상세 칸에 내용을 보여준다.

export type MonthSegment = { key: string; value: number; className: string };

export type MonthColumn = {
  key: string;
  /** 막대 아래 첫 줄(예: "3") */
  label: string;
  /** 막대 아래 둘째 줄(예: 해가 바뀌는 달의 "2025") */
  sub?: string;
  /** 아래에서 위로 쌓는 순서 */
  segments: MonthSegment[];
  /** 흐리게(강조 범위 밖) */
  dim?: boolean;
  /** 스크린 리더용 한 줄 */
  srText: string;
};

const LABEL_H = 30; // px, 막대 아래 라벨 칸

/** 0부터 max를 덮는 깔끔한 눈금(1·2·2.5·5 × 10^n 간격). */
function niceTicks(max: number, count = 3): number[] {
  if (max <= 0) return [0];
  const raw = max / count;
  const mag = 10 ** Math.floor(Math.log10(raw));
  const step = ([1, 2, 2.5, 5, 10].find((s) => s * mag >= raw) ?? 10) * mag;
  const top = Math.ceil(max / step) * step;
  return Array.from({ length: Math.round(top / step) + 1 }, (_, i) => i * step);
}

export function MonthColumns({
  columns,
  title,
  detail,
  hint,
  height = 160,
  minColWidth = 24,
}: {
  columns: MonthColumn[];
  title: string;
  /** i번째 달의 상세 내용 */
  detail: (index: number) => ReactNode;
  hint: string;
  height?: number;
  minColWidth?: number;
}) {
  const [active, setActive] = useState<number | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  // 처음(또는 달 수가 바뀌면) 가장 최근 달이 보이게.
  useLayoutEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollLeft = el.scrollWidth;
  }, [columns.length]);

  const totals = columns.map((c) => c.segments.reduce((s, g) => s + g.value, 0));
  const ticks = niceTicks(Math.max(0, ...totals));
  const top = ticks[ticks.length - 1] || 1;
  const px = (v: number) => (v / top) * height;

  return (
    <figure className="m-0 space-y-2">
      <div className="flex pt-2">
        <div aria-hidden className="relative w-9 shrink-0" style={{ height }}>
          {ticks.map((t) => (
            <span
              key={t}
              className="absolute right-1.5 translate-y-1/2 text-[10px] leading-none text-muted tabular-nums"
              style={{ bottom: px(t) }}
            >
              {formatManShort(t)}
            </span>
          ))}
        </div>

        <div ref={scrollRef} className="min-w-0 flex-1 overflow-x-auto overscroll-x-contain pb-1">
          <ol
            aria-label={title}
            className="relative flex"
            style={{ minWidth: columns.length * minColWidth }}
            onMouseLeave={() => setActive(null)}
          >
            <span aria-hidden className="pointer-events-none absolute inset-x-0 top-0" style={{ height }}>
              {ticks.map((t) => (
                <span
                  key={t}
                  className={`absolute inset-x-0 border-t ${t === 0 ? "border-muted/70" : "border-dashed border-border"}`}
                  style={{ bottom: px(t) }}
                />
              ))}
            </span>

            {columns.map((c, i) => (
              <li
                key={c.key}
                tabIndex={0}
                onMouseEnter={() => setActive(i)}
                onFocus={() => setActive(i)}
                onBlur={() => setActive(null)}
                onClick={() => setActive(i)}
                className={`relative flex min-w-0 flex-1 cursor-default flex-col rounded-md outline-none focus-visible:ring-2 focus-visible:ring-accent ${
                  active === i ? "bg-surface" : ""
                }`}
                style={{ minWidth: minColWidth }}
              >
                <span aria-hidden className="flex items-end justify-center" style={{ height }}>
                  <span className={`flex w-3/5 max-w-6 flex-col-reverse overflow-hidden rounded-t-[3px] ${c.dim ? "opacity-40" : ""}`}>
                    {c.segments.map((g) =>
                      g.value > 0 ? <span key={g.key} className={g.className} style={{ height: Math.max(2, px(g.value)) }} /> : null,
                    )}
                  </span>
                </span>
                <span aria-hidden className="flex flex-col items-center pt-1 text-[10px] leading-tight text-muted tabular-nums" style={{ height: LABEL_H }}>
                  <span className={active === i ? "font-semibold text-foreground" : ""}>{c.label}</span>
                  {c.sub && <span>{c.sub}</span>}
                </span>
                <span className="sr-only">{c.srText}</span>
              </li>
            ))}
          </ol>
        </div>
      </div>

      <div aria-hidden className="min-h-16 rounded-lg bg-surface px-3 py-2 text-xs text-muted tabular-nums">
        {active == null ? hint : detail(active)}
      </div>
    </figure>
  );
}
