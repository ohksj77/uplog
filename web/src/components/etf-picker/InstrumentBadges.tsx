import type { Etf } from "@/lib/api/types";

const MARKET_LABEL = { KR: "국내", US: "미국" } as const;
const TYPE_LABEL = { ETF: "ETF", STOCK: "주식" } as const;

const badge =
  "inline-flex shrink-0 items-center rounded-md px-1.5 py-0.5 text-[11px] font-medium ring-1 ring-inset ring-border text-muted";

/** 시장(국내/미국)과 유형(ETF/주식) 배지. */
export function InstrumentBadges({ etf }: { etf: Pick<Etf, "market" | "type"> }) {
  return (
    <span className="flex shrink-0 items-center gap-1">
      <span className={badge}>{MARKET_LABEL[etf.market]}</span>
      <span className={badge}>{TYPE_LABEL[etf.type]}</span>
    </span>
  );
}
