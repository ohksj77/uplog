"use client";

import { useRef } from "react";
import type { KeyboardEvent } from "react";

import { LOG_MODES, LOG_MODE_LABELS } from "@/components/log-grid/logic";
import type { LogMode } from "@/components/log-grid/types";

export type LogModeToggleProps = {
  value: LogMode;
  onChange: (mode: LogMode) => void;
  /** 라디오 그룹 이름(스크린리더). 기본 "로그 보기" */
  label?: string;
  className?: string;
};

/** 세그먼트 컨트롤: 일별 손익 / 누적 수익률 / 매수 단가 대비. 화살표 키로 이동(라디오 그룹). */
export function LogModeToggle({ value, onChange, label = "로그 보기", className = "" }: LogModeToggleProps) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const dir = e.key === "ArrowRight" || e.key === "ArrowDown" ? 1 : e.key === "ArrowLeft" || e.key === "ArrowUp" ? -1 : 0;
    if (!dir) return;
    e.preventDefault();
    const i = (LOG_MODES.indexOf(value) + dir + LOG_MODES.length) % LOG_MODES.length;
    onChange(LOG_MODES[i]);
    refs.current[i]?.focus();
  };

  return (
    <div
      role="radiogroup"
      aria-label={label}
      onKeyDown={onKeyDown}
      className={`inline-flex rounded-lg bg-[var(--surface)] p-0.5 text-sm ${className}`}
    >
      {LOG_MODES.map((m, i) => {
        const checked = m === value;
        return (
          <button
            key={m}
            ref={(el) => {
              refs.current[i] = el;
            }}
            type="button"
            role="radio"
            aria-checked={checked}
            tabIndex={checked ? 0 : -1}
            onClick={() => onChange(m)}
            className={`rounded-md px-3 py-1.5 whitespace-nowrap outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] motion-safe:transition-colors ${
              checked
                ? "bg-[var(--background)] font-semibold text-[var(--foreground)] shadow-sm"
                : "text-[var(--muted)] hover:text-[var(--foreground)]"
            }`}
          >
            {LOG_MODE_LABELS[m]}
          </button>
        );
      })}
    </div>
  );
}
