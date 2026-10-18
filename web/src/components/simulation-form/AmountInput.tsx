"use client";

import { MAX_AMOUNT } from "@/lib/simulation-params";

export type AmountInputProps = {
  id: string;
  /** 비어 있으면 null */
  value: number | null;
  onChange: (value: number | null) => void;
  describedBy?: string;
  invalid?: boolean;
};

const won = new Intl.NumberFormat("ko-KR");

const QUICK_ADDS = [
  { amount: 100_000, label: "+10만" },
  { amount: 1_000_000, label: "+100만" },
];

// 10억을 넘는 자릿수는 받지 않는다(오류 문구는 폼이 보여 준다).
const MAX_DIGITS = String(MAX_AMOUNT).length + 1;

export function AmountInput({ id, value, onChange, describedBy, invalid }: AmountInputProps) {
  return (
    <div className="space-y-2">
      <div className="relative">
        <input
          id={id}
          type="text"
          inputMode="numeric"
          autoComplete="off"
          aria-describedby={describedBy}
          aria-invalid={invalid || undefined}
          value={value === null ? "" : won.format(value)}
          placeholder="0"
          onChange={(e) => {
            const digits = e.target.value.replace(/\D/g, "").slice(0, MAX_DIGITS);
            onChange(digits === "" ? null : Number(digits));
          }}
          className="h-12 w-full rounded-xl border border-border bg-background pr-10 pl-4 text-right text-lg font-semibold tabular-nums placeholder:text-muted focus-visible:border-accent focus-visible:outline-2 focus-visible:outline-accent/30 aria-invalid:border-gain"
        />
        <span className="pointer-events-none absolute inset-y-0 right-4 flex items-center text-muted" aria-hidden="true">
          원
        </span>
      </div>
      <div className="flex gap-2">
        {QUICK_ADDS.map((q) => (
          <button
            key={q.amount}
            type="button"
            onClick={() => onChange(Math.min((value ?? 0) + q.amount, MAX_AMOUNT))}
            aria-label={`${q.label}원 더하기`}
            className="h-9 rounded-lg bg-surface px-3 text-sm font-medium hover:bg-border focus-visible:outline-2 focus-visible:outline-accent"
          >
            {q.label}
          </button>
        ))}
        <button
          type="button"
          onClick={() => onChange(null)}
          disabled={value === null}
          className="h-9 rounded-lg px-3 text-sm text-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-accent disabled:opacity-40"
        >
          지우기
        </button>
      </div>
    </div>
  );
}
