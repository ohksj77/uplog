"use client";

import { useId } from "react";

export type SegmentOption<T extends string> = { value: T; label: string };

export type SegmentedControlProps<T extends string> = {
  legend: string;
  options: SegmentOption<T>[];
  value: T;
  onChange: (value: T) => void;
  /** 화면에는 숨기고 스크린 리더에만 legend를 읽힌다. */
  hideLegend?: boolean;
  describedBy?: string;
};

/** 라디오 그룹. 방향키 이동은 브라우저 기본 동작을 쓴다. */
export function SegmentedControl<T extends string>({
  legend,
  options,
  value,
  onChange,
  hideLegend,
  describedBy,
}: SegmentedControlProps<T>) {
  const name = useId();
  return (
    <fieldset aria-describedby={describedBy}>
      <legend className={hideLegend ? "sr-only" : "mb-2 text-sm font-medium"}>{legend}</legend>
      <div className="flex gap-1 rounded-xl bg-surface p-1">
        {options.map((o) => (
          <label key={o.value} className="relative flex-1">
            <input
              type="radio"
              name={name}
              value={o.value}
              checked={value === o.value}
              onChange={() => onChange(o.value)}
              className="peer sr-only"
            />
            <span className="flex h-10 cursor-pointer items-center justify-center rounded-lg px-2 text-sm font-medium whitespace-nowrap text-muted transition-colors peer-checked:bg-background peer-checked:text-foreground peer-checked:shadow-sm peer-focus-visible:outline-2 peer-focus-visible:outline-accent hover:text-foreground">
              {o.label}
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
