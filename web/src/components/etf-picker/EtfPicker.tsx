"use client";

import { useId, useRef, useState, type KeyboardEvent } from "react";
import type { Etf, EtfListItem } from "@/lib/api/types";
import { MAX_ETFS } from "@/lib/simulation-params";
import { SampleBadge } from "@/components/etf-picker/SampleBadge";
import { useEtfSearch } from "@/components/etf-picker/useEtfSearch";

export type EtfPickerProps = {
  value: Etf[];
  onChange: (next: Etf[]) => void;
  max?: number;
  /** 바깥 <label htmlFor>가 가리킬 input id */
  inputId?: string;
  /** 도움말·오류 문구 id */
  describedBy?: string;
  invalid?: boolean;
};

const toEtf = ({ code, name, currency, sample }: EtfListItem): Etf => ({ code, name, currency, sample });

/** ARIA combobox(목록형 자동완성) + 고른 종목 칩. */
export function EtfPicker({ value, onChange, max = MAX_ETFS, inputId, describedBy, invalid }: EtfPickerProps) {
  const autoId = useId();
  const id = inputId ?? `${autoId}-input`;
  const listId = `${autoId}-list`;
  const statusId = `${autoId}-status`;
  const inputRef = useRef<HTMLInputElement>(null);

  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);

  const full = value.length >= max;
  const search = useEtfSearch(query, open && !full);
  const items = search.items;
  const activeIndex = active < items.length ? active : -1;
  const selectedCodes = new Set(value.map((e) => e.code));

  function select(item: EtfListItem) {
    if (selectedCodes.has(item.code) || full) return;
    const next = [...value, toEtf(item)];
    onChange(next);
    setQuery("");
    setActive(-1);
    if (next.length >= max) setOpen(false);
  }

  function remove(code: string) {
    onChange(value.filter((e) => e.code !== code));
    inputRef.current?.focus();
  }

  function move(delta: number) {
    if (items.length === 0) return;
    setActive((i) => {
      const cur = i < items.length ? i : -1;
      if (cur === -1) return delta > 0 ? 0 : items.length - 1;
      return (cur + delta + items.length) % items.length;
    });
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    switch (e.key) {
      case "ArrowDown":
        e.preventDefault();
        if (!open) setOpen(true);
        else move(1);
        break;
      case "ArrowUp":
        e.preventDefault();
        if (!open) setOpen(true);
        else move(-1);
        break;
      case "Enter":
        // 목록이 열려 있을 때 Enter로 폼이 제출되지 않게 한다.
        if (open && (activeIndex >= 0 || query)) {
          e.preventDefault();
          if (activeIndex >= 0) select(items[activeIndex]);
        }
        break;
      case "Escape":
        if (open) {
          e.preventDefault();
          setOpen(false);
          setActive(-1);
        } else if (query) {
          setQuery("");
        }
        break;
      case "Backspace":
        if (!query && value.length > 0) onChange(value.slice(0, -1));
        break;
    }
  }

  const expanded = open && !full;
  const statusText =
    search.status === "loading"
      ? "검색 중이에요."
      : search.status === "error"
        ? search.error
        : search.status === "done" && items.length === 0
          ? "찾는 ETF가 없어요. 이름이나 종목코드로 다시 검색해 보세요."
          : "";

  return (
    <div className="space-y-2">
      {value.length > 0 && (
        <ul className="flex flex-wrap gap-2" aria-label="고른 ETF">
          {value.map((etf) => (
            <li
              key={etf.code}
              className="flex max-w-full items-center gap-1.5 rounded-full bg-surface py-1 pr-1 pl-3 text-sm ring-1 ring-border ring-inset"
            >
              <span className="truncate">{etf.name}</span>
              {etf.sample && <SampleBadge />}
              <button
                type="button"
                onClick={() => remove(etf.code)}
                aria-label={`${etf.name} 빼기`}
                className="grid size-6 shrink-0 place-items-center rounded-full text-muted hover:bg-border hover:text-foreground focus-visible:outline-2 focus-visible:outline-accent"
              >
                <svg viewBox="0 0 16 16" className="size-3.5" aria-hidden="true">
                  <path d="M4 4l8 8M12 4l-8 8" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" />
                </svg>
              </button>
            </li>
          ))}
        </ul>
      )}

      <div
        className="relative"
        onBlur={(e) => {
          if (!e.currentTarget.contains(e.relatedTarget)) {
            setOpen(false);
            setActive(-1);
          }
        }}
      >
        <input
          ref={inputRef}
          id={id}
          type="text"
          role="combobox"
          aria-expanded={expanded}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={expanded && activeIndex >= 0 ? `${listId}-${activeIndex}` : undefined}
          aria-describedby={[describedBy, statusId].filter(Boolean).join(" ")}
          aria-invalid={invalid || undefined}
          autoComplete="off"
          spellCheck={false}
          disabled={full}
          value={query}
          placeholder={full ? `최대 ${max}개까지 고를 수 있어요` : "ETF 이름 또는 종목코드"}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
            setActive(-1);
          }}
          onFocus={() => setOpen(true)}
          onClick={() => setOpen(true)}
          onKeyDown={onKeyDown}
          className="h-12 w-full rounded-xl border border-border bg-background px-4 text-base placeholder:text-muted focus-visible:border-accent focus-visible:outline-2 focus-visible:outline-accent/30 disabled:cursor-not-allowed disabled:bg-surface aria-invalid:border-gain"
        />

        <ul
          id={listId}
          role="listbox"
          aria-label="ETF 검색 결과"
          hidden={!expanded || items.length === 0}
          className="absolute inset-x-0 top-full z-20 mt-1 max-h-72 overflow-y-auto rounded-xl border border-border bg-background p-1 shadow-lg"
        >
          {items.map((item, i) => {
            const picked = selectedCodes.has(item.code);
            return (
              <li
                key={item.code}
                id={`${listId}-${i}`}
                role="option"
                aria-selected={i === activeIndex}
                aria-disabled={picked || undefined}
                onMouseDown={(e) => e.preventDefault()}
                onMouseEnter={() => setActive(i)}
                onClick={() => select(item)}
                className={`flex cursor-pointer items-center gap-2 rounded-lg px-3 py-2.5 text-sm ${
                  i === activeIndex ? "bg-surface" : ""
                } ${picked ? "cursor-default text-muted" : ""}`}
              >
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium">{item.name}</span>
                  <span className="block text-xs text-muted">{item.code}</span>
                </span>
                {item.sample && <SampleBadge />}
                {picked && <span className="shrink-0 text-xs text-muted">선택됨</span>}
              </li>
            );
          })}
        </ul>

        <p
          id={statusId}
          role="status"
          aria-live="polite"
          className={
            expanded && statusText && items.length === 0
              ? `absolute inset-x-0 top-full z-20 mt-1 rounded-xl border border-border bg-background px-4 py-3 text-sm shadow-lg ${
                  search.status === "error" ? "text-gain" : "text-muted"
                }`
              : "sr-only"
          }
        >
          {expanded ? statusText : ""}
        </p>
      </div>
    </div>
  );
}
