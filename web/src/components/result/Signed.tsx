import type { ReactNode } from "react";
import { toneOf, type Tone } from "@/lib/format";

const TONE_CLASS: Record<Tone, string> = {
  gain: "text-gain",
  loss: "text-loss",
  flat: "text-muted",
};

/** 부호에 따라 이익 빨강 / 손실 파랑으로 칠한다. */
export function Signed({ value, className = "", children }: { value: number; className?: string; children: ReactNode }) {
  return <span className={`${TONE_CLASS[toneOf(value)]} ${className}`}>{children}</span>;
}
