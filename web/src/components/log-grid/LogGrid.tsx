"use client";

import { memo, useCallback, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { FocusEvent, KeyboardEvent, MouseEvent, PointerEvent } from "react";

import {
  LOG_MODE_LABELS,
  LOG_ROW_LABELS,
  buildLogLayout,
  formatLogDate,
  formatLogValue,
  logCellLabel,
  logColor,
  moveLogIndex,
  type LogLayout,
  type LogMove,
} from "@/components/log-grid/logic";
import type { LogMode, LogGridProps } from "@/components/log-grid/types";
import type { SimulationDay } from "@/lib/api/types";
import { formatWon, toneOf } from "@/lib/format";

// 칸 크기(px). Tailwind 클래스(size-3, gap-[3px])와 맞춘다.
const CELL = 12;
const GAP = 3;
const PITCH = CELL + GAP;
/** 스크롤 영역 안쪽 여백 — 선택 테두리(3px)가 잘리지 않게. */
const PAD = 4;

const KEY_MOVES: Record<string, LogMove> = {
  ArrowUp: "prevDay",
  ArrowDown: "nextDay",
  ArrowLeft: "prevWeek",
  ArrowRight: "nextWeek",
  Home: "first",
  End: "last",
};

const ROW_NAMES = ["월요일", "화요일", "수요일", "목요일", "금요일"] as const;

type Tip = { index: number; x: number; y: number; align: "start" | "center" | "end"; source: "hover" | "focus" };

export function LogGrid({ days, mode, label, onSelectDay, selectedDate = null, range }: LogGridProps) {
  const rangeFrom = range?.from;
  const rangeTo = range?.to;
  const layout = useMemo(
    () => buildLogLayout(days, mode, rangeFrom && rangeTo ? { from: rangeFrom, to: rangeTo } : undefined),
    [days, mode, rangeFrom, rangeTo],
  );
  const labels = useMemo(
    () => days.map((day, i) => {
      const [r, c] = layout.positions[i];
      const cell = layout.rows[r]?.[c];
      return logCellLabel(day, cell?.kind === "day" ? cell.value : null, mode);
    }),
    [days, layout, mode],
  );

  const wrapRef = useRef<HTMLDivElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [focusedDate, setFocusedDate] = useState<string | null>(null);
  const [tip, setTip] = useState<Tip | null>(null);

  const selectedIndex = selectedDate ? layout.indexByDate.get(selectedDate) ?? null : null;
  const focusIndex = (focusedDate ? layout.indexByDate.get(focusedDate) : undefined) ?? selectedIndex ?? days.length - 1;

  // 처음(또는 기간이 바뀌면) 가장 최근 쪽이 보이게.
  useLayoutEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollLeft = el.scrollWidth;
  }, [layout.columns]);

  const measure = useCallback((cell: HTMLElement, index: number, source: Tip["source"]): Tip | null => {
    const wrap = wrapRef.current;
    const scroller = scrollRef.current;
    if (!wrap || !scroller) return null;
    const w = wrap.getBoundingClientRect();
    const s = scroller.getBoundingClientRect();
    const c = cell.getBoundingClientRect();
    if (c.right < s.left || c.left > s.right) return null; // 스크롤 밖
    const x = c.left + c.width / 2 - w.left;
    const align = x < 96 ? "start" : x > w.width - 96 ? "end" : "center";
    return { index, x, y: c.top - w.top, align, source };
  }, []);

  const cellOf = (target: EventTarget | null): [HTMLElement, number] | null => {
    const el = (target as HTMLElement | null)?.closest?.<HTMLElement>("[data-i]");
    if (!el) return null;
    return [el, Number(el.dataset.i)];
  };

  const onPointerOver = (e: PointerEvent<HTMLDivElement>) => {
    const hit = cellOf(e.target);
    if (!hit) return;
    if (tip?.source === "hover" && tip.index === hit[1]) return;
    setTip(measure(hit[0], hit[1], "hover"));
  };

  const onPointerLeave = () => setTip((t) => (t?.source === "hover" ? null : t));

  const onFocus = (e: FocusEvent<HTMLDivElement>) => {
    const hit = cellOf(e.target);
    if (!hit) return;
    const [el, index] = hit;
    setFocusedDate(days[index]?.date ?? null);
    // focus()가 스크롤을 옮긴 뒤 위치를 잰다.
    requestAnimationFrame(() => {
      if (document.activeElement === el) setTip((t) => (t?.source === "hover" ? t : measure(el, index, "focus")));
    });
  };

  const onBlur = (e: FocusEvent<HTMLDivElement>) => {
    if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setTip((t) => (t?.source === "focus" ? null : t));
  };

  const onClick = (e: MouseEvent<HTMLDivElement>) => {
    const hit = cellOf(e.target);
    if (!hit) return;
    const day = days[hit[1]];
    if (day) onSelectDay?.(day);
  };

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (days.length === 0) return;
    const move = KEY_MOVES[e.key];
    if (move) {
      e.preventDefault();
      const next = moveLogIndex(days, layout.indexByDate, focusIndex, move);
      e.currentTarget.querySelector<HTMLElement>(`[data-i="${next}"]`)?.focus();
      return;
    }
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      const day = days[focusIndex];
      if (day) onSelectDay?.(day);
    } else if (e.key === "Escape") {
      setTip(null);
      if (selectedDate) onSelectDay?.(null);
    }
  };

  const onScroll = () => setTip((t) => (t ? null : t));

  if (days.length === 0) {
    return (
      <div role="note" aria-label={label} className="rounded-md bg-[var(--surface)] px-3 py-6 text-center text-sm text-[var(--muted)]">
        표시할 거래일이 없어요
      </div>
    );
  }

  const tipDay = tip ? days[tip.index] : undefined;

  return (
    <div ref={wrapRef} className="relative">
      <div className="flex">
        {/* 요일 라벨(보이는 것). 스크린리더는 각 행의 rowheader를 읽는다. */}
        <div aria-hidden className="flex shrink-0 flex-col gap-[3px] pr-1.5 text-[10px] leading-3 text-[var(--muted)]" style={{ paddingTop: PAD + 16 }}>
          {LOG_ROW_LABELS.map((d, r) => (
            <span key={d} className="h-3">
              {r % 2 === 0 ? d : ""}
            </span>
          ))}
        </div>

        <div ref={scrollRef} onScroll={onScroll} className="min-w-0 flex-1 overflow-x-auto overscroll-x-contain [scrollbar-width:thin]">
          <div className="inline-block align-top" style={{ padding: PAD }}>
            <MonthLabels layout={layout} />
            <div
              role="grid"
              aria-label={`${label} — ${LOG_MODE_LABELS[mode]}`}
              aria-readonly="true"
              className="flex flex-col gap-[3px]"
              onKeyDown={onKeyDown}
              onPointerOver={onPointerOver}
              onPointerLeave={onPointerLeave}
              onClick={onClick}
              onFocus={onFocus}
              onBlur={onBlur}
            >
              <LogRows
                layout={layout}
                labels={labels}
                showMarkers={mode !== "costBasis"}
                focusIndex={focusIndex}
                selectedIndex={selectedIndex}
              />
            </div>
          </div>
        </div>
      </div>

      {tip && tipDay && <Tooltip tip={tip} day={tipDay} mode={mode} layout={layout} />}
    </div>
  );
}

function MonthLabels({ layout }: { layout: LogLayout }) {
  return (
    <div aria-hidden className="relative h-4 text-[10px] leading-3 text-[var(--muted)]" style={{ width: layout.columns * PITCH - GAP }}>
      {layout.monthLabels.map((m) => (
        <span key={m.column} className="absolute top-0 whitespace-nowrap" style={{ left: m.column * PITCH }}>
          {m.text}
        </span>
      ))}
    </div>
  );
}

type RowsProps = {
  layout: LogLayout;
  labels: string[];
  showMarkers: boolean;
  focusIndex: number;
  selectedIndex: number | null;
};

/** 칸 780개를 그리는 부분. 호버(툴팁)로는 다시 그려지지 않도록 memo. */
const LogRows = memo(function LogRows({ layout, labels, showMarkers, focusIndex, selectedIndex }: RowsProps) {
  return layout.rows.map((row, r) => (
    <div key={r} role="row" className="flex gap-[3px]">
      <span role="rowheader" className="sr-only">
        {ROW_NAMES[r]}
      </span>
      {row.map((cell, c) => {
        if (cell.kind === "out") return <div key={c} role="gridcell" aria-hidden className="size-3 shrink-0" />;
        if (cell.kind === "gap") {
          return (
            <div
              key={c}
              role="gridcell"
              aria-label={`${formatLogDate(cell.date)}, 거래 없는 날`}
              className="size-3 shrink-0 rounded-[2px] shadow-[inset_0_0_0_1px_var(--log-empty)]"
            />
          );
        }
        const selected = cell.index === selectedIndex;
        return (
          <div
            key={c}
            role="gridcell"
            data-i={cell.index}
            tabIndex={cell.index === focusIndex ? 0 : -1}
            aria-label={labels[cell.index]}
            aria-selected={selected}
            className="relative size-3 shrink-0 cursor-pointer rounded-[2px] outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)]"
            style={{
              backgroundColor: logColor(cell.level),
              boxShadow: selected ? "0 0 0 1px var(--background), 0 0 0 3px var(--foreground)" : undefined,
            }}
          >
            {showMarkers && cell.purchased && (
              <span
                aria-hidden
                className="pointer-events-none absolute top-1/2 left-1/2 size-1 -translate-x-1/2 -translate-y-1/2 rounded-full bg-[var(--foreground)]"
                style={{ boxShadow: "0 0 0 1px var(--background)" }}
              />
            )}
          </div>
        );
      })}
    </div>
  ));
});

function Tooltip({ tip, day, mode, layout }: { tip: Tip; day: SimulationDay; mode: LogMode; layout: LogLayout }) {
  const [r, c] = layout.positions[tip.index];
  const cell = layout.rows[r]?.[c];
  const value = cell?.kind === "day" ? cell.value : null;
  const tone = value === null ? "flat" : toneOf(Math.abs(value) < 0.00005 ? 0 : value);
  const shiftX = tip.align === "start" ? `-${CELL / 2}px` : tip.align === "end" ? `calc(-100% + ${CELL / 2}px)` : "-50%";

  return (
    <div
      aria-hidden
      className="pointer-events-none absolute z-20 w-max max-w-[220px] rounded-md bg-[var(--foreground)] px-2.5 py-1.5 text-xs leading-5 text-[var(--background)] shadow-lg motion-safe:transition-opacity"
      style={{ left: tip.x, top: tip.y, transform: `translate(${shiftX}, calc(-100% - 6px))` }}
    >
      <div className="font-semibold">{formatLogDate(day.date)}</div>
      <div className="flex items-baseline justify-between gap-3">
        <span className="opacity-75">{LOG_MODE_LABELS[mode]}</span>
        <span
          className="font-semibold tabular-nums"
          style={{ color: tone === "gain" ? "var(--gain-2)" : tone === "loss" ? "var(--loss-2)" : undefined }}
        >
          {formatLogValue(value, mode)}
        </span>
      </div>
      <div className="flex items-baseline justify-between gap-3">
        <span className="opacity-75">평가금액</span>
        <span className="tabular-nums">{formatWon(day.value)}</span>
      </div>
      {day.purchased && <div className="mt-0.5 text-[11px] opacity-90">● 매수일</div>}
    </div>
  );
}
