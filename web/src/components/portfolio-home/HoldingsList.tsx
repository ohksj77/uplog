import Link from "next/link";
import type { Holding } from "@/lib/api/portfolio-types";
import { Signed } from "@/components/result/Signed";
import { formatPercent, formatSignedWon, formatWon } from "@/lib/format";
import { holdingHref } from "@/lib/routes";

const weightText = (w: number) => `${(w * 100).toFixed(1)}%`;

/** 현재 보유 종목(평가금액 큰 순). 줄을 누르면 종목 상세로. */
export function HoldingsList({ holdings }: { holdings: Holding[] }) {
  if (holdings.length === 0) {
    return <p className="rounded-2xl bg-surface px-5 py-8 text-center text-sm text-muted">지금 보유한 종목이 없어요</p>;
  }
  return (
    <ul className="divide-y divide-border overflow-hidden rounded-2xl bg-surface">
      {holdings.map((h) => (
        <li key={h.instrument.code}>
          <Link
            href={holdingHref(h.instrument.code)}
            className="block px-4 py-3 transition-colors hover:bg-border focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-accent"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="truncate font-semibold">{h.instrument.name}</p>
                <p className="text-xs text-muted">
                  {h.instrument.code}
                  {h.instrument.market === "US" && " · 해외"}
                </p>
              </div>
              <div className="shrink-0 text-right tabular-nums">
                <p className="font-semibold">{formatWon(h.value)}</p>
                <p className="text-xs">
                  <Signed value={h.profit}>
                    {formatSignedWon(h.profit)} ({formatPercent(h.returnRate)})
                  </Signed>
                </p>
              </div>
            </div>
            <div className="mt-2 flex items-center gap-2 text-xs text-muted">
              <span aria-hidden className="h-1.5 flex-1 overflow-hidden rounded-full bg-border">
                <span className="block h-full rounded-full bg-foreground/25" style={{ width: `${Math.min(100, h.weight * 100)}%` }} />
              </span>
              <span className="w-16 text-right tabular-nums">비중 {weightText(h.weight)}</span>
            </div>
          </Link>
        </li>
      ))}
    </ul>
  );
}
