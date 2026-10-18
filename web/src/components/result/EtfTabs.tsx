"use client";

import { useRef, type KeyboardEvent } from "react";
import type { Etf } from "@/lib/api/types";

type EtfTabsProps = {
  etfs: Etf[];
  selected: number;
  onSelect: (index: number) => void;
  /** 탭 i가 제어하는 패널 id */
  panelId: (index: number) => string;
  tabId: (index: number) => string;
};

export function EtfTabs({ etfs, selected, onSelect, panelId, tabId }: EtfTabsProps) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);

  const onKeyDown = (e: KeyboardEvent) => {
    const last = etfs.length - 1;
    const next =
      e.key === "ArrowRight" ? (selected === last ? 0 : selected + 1)
      : e.key === "ArrowLeft" ? (selected === 0 ? last : selected - 1)
      : e.key === "Home" ? 0
      : e.key === "End" ? last
      : null;
    if (next == null) return;
    e.preventDefault();
    onSelect(next);
    refs.current[next]?.focus();
  };

  return (
    <div role="tablist" aria-label="ETF 선택" className="flex gap-1 rounded-xl bg-surface p-1" onKeyDown={onKeyDown}>
      {etfs.map((etf, i) => (
        <button
          key={etf.code}
          ref={(el) => {
            refs.current[i] = el;
          }}
          id={tabId(i)}
          type="button"
          role="tab"
          aria-selected={i === selected}
          aria-controls={panelId(i)}
          tabIndex={i === selected ? 0 : -1}
          onClick={() => onSelect(i)}
          className={`min-w-0 flex-1 truncate rounded-lg px-2 py-2 text-sm font-medium transition-colors ${
            i === selected ? "bg-background text-foreground shadow-sm" : "text-muted"
          }`}
        >
          {etf.name}
        </button>
      ))}
    </div>
  );
}
