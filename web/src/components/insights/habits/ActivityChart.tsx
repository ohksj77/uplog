"use client";

import { useState } from "react";
import type { HabitsInsight } from "@/lib/api/portfolio-types";

// 월별 매수·매도 체결 수 막대. 회전율은 막대 대신 설명 줄과 요약 문장으로 보여준다(축을 둘로 나누지 않는다).

type Month = HabitsInsight["activity"][number];

const HEIGHT = 112; // px, 막대 영역 높이

const monthLabel = (m: string) => `${m.slice(0, 4)}년 ${Number(m.slice(5, 7))}월`;
const pct = (v: number) => `${(v * 100).toFixed(1)}%`;

export function activitySummary(activity: Month[]): string {
  if (activity.length === 0) return "아직 체결 기록이 없어요.";
  const buys = activity.reduce((a, m) => a + m.buys, 0);
  const sells = activity.reduce((a, m) => a + m.sells, 0);
  const busiest = activity.reduce((b, m) => (m.buys + m.sells > b.buys + b.sells ? m : b), activity[0]);
  const avgTurnover = activity.reduce((a, m) => a + m.turnover, 0) / activity.length;
  const quiet = activity.filter((m) => m.buys + m.sells === 0).length;
  return (
    `${activity.length}개월 동안 매수 ${buys}번, 매도 ${sells}번 체결했어요. ` +
    (busiest.buys + busiest.sells > 0
      ? `가장 많이 거래한 달은 ${monthLabel(busiest.month)}(매수 ${busiest.buys}번, 매도 ${busiest.sells}번)이에요. `
      : "") +
    `월 회전율은 평균 ${pct(avgTurnover)}` +
    (quiet > 0 ? `이고, 한 번도 거래하지 않은 달은 ${quiet}개월이에요.` : "예요.")
  );
}

export function ActivityChart({ activity }: { activity: Month[] }) {
  const [active, setActive] = useState<Month | null>(null);
  const max = Math.max(1, ...activity.map((m) => Math.max(m.buys, m.sells)));
  const h = (n: number) => (n === 0 ? 0 : Math.max(3, (n / max) * HEIGHT));

  return (
    <section aria-labelledby="habits-activity" className="space-y-3">
      <h2 id="habits-activity" className="text-lg font-semibold">
        얼마나 자주 사고팔았나
      </h2>
      <p className="text-sm break-keep">{activitySummary(activity)}</p>

      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted" aria-hidden>
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-[3px] bg-trade-buy" /> 매수
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-[3px] bg-trade-sell" /> 매도
        </span>
        <span>점선 {max}번</span>
      </div>

      <figure className="m-0 space-y-2">
        <div className="relative border-b border-border" style={{ height: HEIGHT }}>
          <span aria-hidden className="absolute inset-x-0 top-0 border-t border-dashed border-border" />
          <ol aria-label="월별 매수·매도 체결 수" className="relative flex h-full items-end" onMouseLeave={() => setActive(null)}>
            {activity.map((m) => (
              <li
                key={m.month}
                tabIndex={0}
                onMouseEnter={() => setActive(m)}
                onFocus={() => setActive(m)}
                onBlur={() => setActive(null)}
                className={`flex h-full min-w-0 flex-1 items-end justify-center gap-px rounded-sm outline-none focus-visible:ring-2 focus-visible:ring-accent ${
                  active?.month === m.month ? "bg-surface" : ""
                }`}
              >
                <span aria-hidden className="w-[38%] max-w-2.5 rounded-t-[3px] bg-trade-buy" style={{ height: h(m.buys) }} />
                <span aria-hidden className="w-[38%] max-w-2.5 rounded-t-[3px] bg-trade-sell" style={{ height: h(m.sells) }} />
                <span className="sr-only">
                  {monthLabel(m.month)}: 매수 {m.buys}번, 매도 {m.sells}번, 회전율 {pct(m.turnover)}
                </span>
              </li>
            ))}
          </ol>
        </div>
        <div aria-hidden className="relative h-4 text-[11px] text-muted tabular-nums">
          {activity.map((m, i) =>
            m.month.endsWith("-01") || m.month.endsWith("-07") ? (
              <span
                key={m.month}
                className="absolute -translate-x-1/2 whitespace-nowrap"
                style={{ left: `${((i + 0.5) / activity.length) * 100}%` }}
              >
                {m.month.slice(2, 4)}.{m.month.slice(5, 7)}
              </span>
            ) : null,
          )}
        </div>
        <p aria-hidden className="min-h-10 rounded-lg bg-surface px-3 py-2 text-xs text-muted tabular-nums">
          {active ? (
            <>
              <span className="font-medium text-foreground">{monthLabel(active.month)}</span> 매수 {active.buys}번 · 매도{" "}
              {active.sells}번 · 회전율 {pct(active.turnover)}
            </>
          ) : (
            "막대에 마우스를 올리거나 Tab으로 옮기면 그달 체결 수와 회전율을 보여줘요."
          )}
        </p>
        <figcaption className="text-xs text-muted">회전율은 그달 매도 금액을 그달 평균 평가금으로 나눈 값이에요.</figcaption>
      </figure>
    </section>
  );
}
