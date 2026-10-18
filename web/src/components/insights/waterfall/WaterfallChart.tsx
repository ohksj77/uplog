"use client";

import { useState } from "react";
import type { WaterfallStep } from "@/components/insights/waterfall/steps";
import { formatSignedWon, formatWonShort } from "@/lib/format";

// 가로 막대 폭포수. 위에서 아래로 항목이 쌓이고, 막대 끝을 점선으로 이어 다음 막대의 출발점을 보여준다.
// 막대 영역은 퍼센트 좌표라 화면 폭을 재지 않아도 된다.

const ROW = 32; // px, 행 높이
const BAR_TOP = 7;
const BAR_H = 18;

const signedShort = (v: number) => `${v > 0 ? "+" : ""}${formatWonShort(v)}`;

export function WaterfallChart({ steps, title }: { steps: WaterfallStep[]; title: string }) {
  const [active, setActive] = useState<string | null>(null);

  const points = [0, ...steps.flatMap((s) => [s.start, s.end])];
  const lo = Math.min(...points);
  const hi = Math.max(...points);
  const span = hi - lo || 1;
  const pct = (v: number) => ((v - lo) / span) * 100;
  const zero = pct(0);

  const shown = steps.find((s) => s.key === active) ?? null;

  return (
    <figure className="m-0 space-y-2">
      <ol aria-label={title} className="relative" onMouseLeave={() => setActive(null)}>
        {steps.map((s, i) => {
          const isTotal = s.kind === "TOTAL";
          const left = pct(Math.min(s.start, s.end));
          const width = Math.max(0.6, Math.abs(pct(s.end) - pct(s.start)));
          const color = s.amount >= 0 ? "bg-gain" : "bg-loss";
          const next = steps[i + 1];
          return (
            <li
              key={s.key}
              tabIndex={0}
              onMouseEnter={() => setActive(s.key)}
              onFocus={() => setActive(s.key)}
              onBlur={() => setActive(null)}
              className={`grid grid-cols-[5.75rem_minmax(0,1fr)_4.25rem] items-center gap-2 rounded-md outline-none focus-visible:ring-2 focus-visible:ring-accent sm:grid-cols-[9rem_minmax(0,1fr)_5rem] ${
                isTotal ? "mt-1 border-t border-border pt-1" : ""
              } ${active === s.key ? "bg-surface" : ""}`}
              style={{ height: ROW + (isTotal ? 5 : 0) }}
            >
              <span aria-hidden className={`truncate text-xs ${isTotal ? "font-semibold" : "text-muted"}`} title={s.label}>
                {s.label}
              </span>
              <span aria-hidden className="relative h-full">
                <span className="absolute inset-y-0 w-px bg-muted/60" style={{ left: `${zero}%` }} />
                <span
                  className={`absolute rounded-[4px] ${color} ${isTotal ? "" : "opacity-80"}`}
                  style={{ left: `${left}%`, width: `${width}%`, top: BAR_TOP, height: BAR_H }}
                />
                {next && next.kind !== "TOTAL" && (
                  <span
                    className="absolute border-l border-dashed border-muted"
                    style={{ left: `${pct(s.end)}%`, top: BAR_TOP + BAR_H, height: ROW - BAR_H + 1 }}
                  />
                )}
              </span>
              <span
                aria-hidden
                className={`text-right text-xs tabular-nums ${isTotal ? "font-semibold" : ""} ${s.amount > 0 ? "text-gain" : s.amount < 0 ? "text-loss" : "text-muted"}`}
              >
                {signedShort(s.amount)}
              </span>
              <span className="sr-only">
                {s.label} {formatSignedWon(s.amount)}
                {!isTotal && `, 누적 ${formatSignedWon(s.end)}`}
              </span>
            </li>
          );
        })}
      </ol>

      <p aria-hidden className="min-h-10 rounded-lg bg-surface px-3 py-2 text-xs text-muted tabular-nums">
        {shown ? <Detail step={shown} /> : "막대에 마우스를 올리거나 Tab으로 옮기면 누적 손익을 보여줘요."}
      </p>
    </figure>
  );
}

function Detail({ step }: { step: WaterfallStep }) {
  if (step.kind === "TOTAL") {
    return (
      <>
        <span className="font-medium text-foreground">기간 손익</span> {formatSignedWon(step.amount)}
      </>
    );
  }
  return (
    <>
      <span className="font-medium text-foreground">{step.label}</span> {formatSignedWon(step.amount)} · 누적{" "}
      {formatSignedWon(step.start)} → {formatSignedWon(step.end)}
      {step.members.length > 0 && (
        <span className="mt-1 block">{step.members.map((m) => `${m.label} ${signedShort(m.amount)}`).join(", ")}</span>
      )}
    </>
  );
}
