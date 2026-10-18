"use client";

import { memo, useCallback, useLayoutEffect, useMemo, useRef, useState } from "react";
import type { FocusEvent, KeyboardEvent, MouseEvent, PointerEvent } from "react";

import {
  LOG_ROW_LABELS,
  dayNumber,
  formatLogDate,
  layoutLogDates,
  logColor,
  moveLogIndex,
  type LogDateLayout,
  type LogMove,
} from "@/components/log-grid/logic";
import type { LogGridBand, LogGridBaseProps, LogGridCell, LogTooltip } from "@/components/log-grid/types";

// 범용 로그 격자: 배치·키보드·툴팁·가로 스크롤. 칸의 색·문구는 부르는 쪽(LogGrid, 계좌 로그)이 정한다.

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

export function LogGridBase({
  cells,
  label,
  selectedDate = null,
  range,
  onActivate,
  onClear,
  bands,
  emptyText = "표시할 거래일이 없어요",
}: LogGridBaseProps) {
  const rangeFrom = range?.from;
  const rangeTo = range?.to;
  const layout = useMemo(
    () => layoutLogDates(cells.map((c) => c.date), rangeFrom && rangeTo ? { from: rangeFrom, to: rangeTo } : undefined),
    [cells, rangeFrom, rangeTo],
  );

  const wrapRef = useRef<HTMLDivElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [focusedDate, setFocusedDate] = useState<string | null>(null);
  const [tip, setTip] = useState<Tip | null>(null);

  const selectedIndex = selectedDate ? layout.indexByDate.get(selectedDate) ?? null : null;
  const focusIndex = (focusedDate ? layout.indexByDate.get(focusedDate) : undefined) ?? selectedIndex ?? cells.length - 1;

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

  const activate = (index: number) => {
    const cell = cells[index];
    if (cell && cell.activatable !== false) onActivate?.(index);
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
    setFocusedDate(cells[index]?.date ?? null);
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
    if (hit) activate(hit[1]);
  };

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    if (cells.length === 0) return;
    const move = KEY_MOVES[e.key];
    if (move) {
      e.preventDefault();
      const next = moveLogIndex(cells, layout.indexByDate, focusIndex, move);
      e.currentTarget.querySelector<HTMLElement>(`[data-i="${next}"]`)?.focus();
      return;
    }
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      activate(focusIndex);
    } else if (e.key === "Escape") {
      setTip(null);
      if (selectedDate) onClear?.();
    }
  };

  const onScroll = () => setTip((t) => (t ? null : t));

  if (cells.length === 0) {
    return (
      <div role="note" aria-label={label} className="rounded-md bg-[var(--surface)] px-3 py-6 text-center text-sm text-[var(--muted)]">
        {emptyText}
      </div>
    );
  }

  const tipCell = tip ? cells[tip.index] : undefined;

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
              aria-label={label}
              aria-readonly="true"
              className="flex flex-col gap-[3px]"
              onKeyDown={onKeyDown}
              onPointerOver={onPointerOver}
              onPointerLeave={onPointerLeave}
              onClick={onClick}
              onFocus={onFocus}
              onBlur={onBlur}
            >
              <LogRows layout={layout} cells={cells} focusIndex={focusIndex} selectedIndex={selectedIndex} />
            </div>
            {bands && bands.length > 0 && <Bands layout={layout} bands={bands} />}
          </div>
        </div>
      </div>

      {tip && tipCell && <Tooltip tip={tip} content={tipCell.tooltip} />}
    </div>
  );
}

function MonthLabels({ layout }: { layout: LogDateLayout }) {
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

/** 기간 표시: 해당 열 아래 점선과 짧은 라벨. */
function Bands({ layout, bands }: { layout: LogDateLayout; bands: readonly LogGridBand[] }) {
  const col = (iso: string) => Math.floor((dayNumber(iso) - layout.start) / 7);
  const spans = bands
    .map((b) => ({ ...b, c0: Math.max(0, col(b.from)), c1: Math.min(layout.columns - 1, col(b.to)) }))
    .filter((b) => b.c1 >= b.c0);
  if (spans.length === 0) return null;
  return (
    <div aria-hidden className="relative mt-1.5 h-4 text-[10px] leading-3 text-[var(--muted)]" style={{ width: layout.columns * PITCH - GAP }}>
      {spans.map((b) => (
        <span
          key={`${b.from}-${b.to}`}
          className="absolute top-0 truncate border-t border-dashed border-[var(--muted)] pt-0.5"
          style={{ left: b.c0 * PITCH, width: (b.c1 - b.c0 + 1) * PITCH - GAP }}
        >
          {b.label}
        </span>
      ))}
    </div>
  );
}

type RowsProps = {
  layout: LogDateLayout;
  cells: readonly LogGridCell[];
  focusIndex: number;
  selectedIndex: number | null;
};

/** 칸 수백 개를 그리는 부분. 호버(툴팁)로는 다시 그려지지 않도록 memo. */
const LogRows = memo(function LogRows({ layout, cells, focusIndex, selectedIndex }: RowsProps) {
  return layout.rows.map((row, r) => (
    <div key={r} role="row" className="flex gap-[3px]">
      <span role="rowheader" className="sr-only">
        {ROW_NAMES[r]}
      </span>
      {row.map((slot, c) => {
        if (slot.kind === "out") return <div key={c} role="gridcell" aria-hidden className="size-3 shrink-0" />;
        if (slot.kind === "gap") {
          return (
            <div
              key={c}
              role="gridcell"
              aria-label={`${formatLogDate(slot.date)}, 거래 없는 날`}
              className="size-3 shrink-0 rounded-[2px] shadow-[inset_0_0_0_1px_var(--log-empty)]"
            />
          );
        }
        const cell = cells[slot.index];
        const selected = slot.index === selectedIndex;
        const ring = selected ? "0 0 0 1px var(--background), 0 0 0 3px var(--foreground)" : null;
        // 기록 없는 칸은 진한 테두리만 — 휴장(옅은 테두리, 포커스 안 됨)과 구분된다.
        const hollow = cell.hollow ? "inset 0 0 0 1px var(--muted)" : null;
        return (
          <div
            key={c}
            role="gridcell"
            data-i={slot.index}
            tabIndex={slot.index === focusIndex ? 0 : -1}
            aria-label={cell.label}
            aria-selected={selected}
            className={`relative size-3 shrink-0 rounded-[2px] outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--accent)] ${
              cell.activatable === false ? "cursor-default" : "cursor-pointer"
            } ${cell.hollow ? "opacity-60" : ""}`}
            style={{
              backgroundColor: cell.hollow ? "transparent" : logColor(cell.level),
              boxShadow: [ring, hollow].filter(Boolean).join(", ") || undefined,
            }}
          >
            {cell.marker === "dot" && (
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

const TONE_COLOR = { gain: "var(--gain-2)", loss: "var(--loss-2)", flat: undefined } as const;

function Tooltip({ tip, content }: { tip: Tip; content: LogTooltip }) {
  const shiftX = tip.align === "start" ? `-${CELL / 2}px` : tip.align === "end" ? `calc(-100% + ${CELL / 2}px)` : "-50%";

  return (
    <div
      aria-hidden
      className="pointer-events-none absolute z-20 w-max max-w-[220px] rounded-md bg-[var(--foreground)] px-2.5 py-1.5 text-xs leading-5 text-[var(--background)] shadow-lg motion-safe:transition-opacity"
      style={{ left: tip.x, top: tip.y, transform: `translate(${shiftX}, calc(-100% - 6px))` }}
    >
      <div className="font-semibold">{content.title}</div>
      {content.rows.map((row) => (
        <div key={row.label} className="flex items-baseline justify-between gap-3">
          <span className="opacity-75">{row.label}</span>
          <span className={`tabular-nums ${row.tone ? "font-semibold" : ""}`} style={{ color: row.tone ? TONE_COLOR[row.tone] : undefined }}>
            {row.value}
          </span>
        </div>
      ))}
      {content.note && <div className="mt-0.5 text-[11px] opacity-90">{content.note}</div>}
    </div>
  );
}
