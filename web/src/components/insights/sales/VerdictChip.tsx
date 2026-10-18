import type { SaleVerdict } from "@/lib/api/portfolio-types";

export const VERDICT_LABEL: Record<SaleVerdict, string> = {
  GOOD: "판 게 나았음",
  BAD: "놓쳤음",
  PENDING: "판정 대기",
};

const TONE: Record<SaleVerdict, string> = {
  GOOD: "border-gain/40 text-gain",
  BAD: "border-loss/40 text-loss",
  PENDING: "border-border text-muted",
};

export function VerdictChip({ verdict }: { verdict: SaleVerdict }) {
  return (
    <span className={`inline-flex h-6 shrink-0 items-center rounded-full border px-2 text-xs font-medium whitespace-nowrap ${TONE[verdict]}`}>
      {VERDICT_LABEL[verdict]}
    </span>
  );
}
