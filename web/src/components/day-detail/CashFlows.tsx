import Link from "next/link";
import type { CashFlowItem } from "@/lib/api/portfolio-types";
import { formatWon } from "@/lib/format";

const LABEL = { DEPOSIT: "입금", WITHDRAWAL: "출금", DIVIDEND: "배당" } as const;

/** 입금·출금·배당. 금액 부호는 종류로 정한다(출금은 −). */
export function CashFlows({ items }: { items: CashFlowItem[] }) {
  return (
    <section aria-labelledby="cash-heading" className="space-y-3">
      <h2 id="cash-heading" className="text-lg font-semibold">
        입출금·배당
      </h2>
      {items.length === 0 ? (
        <p className="rounded-2xl bg-surface px-5 py-8 text-center text-sm text-muted">이날은 입출금이나 배당이 없었어요</p>
      ) : (
        <ul className="divide-y divide-border rounded-2xl bg-surface">
          {items.map((c, i) => {
            const out = c.type === "WITHDRAWAL";
            return (
              <li key={`${c.type}-${i}`} className="flex items-center justify-between gap-3 px-4 py-3 text-sm">
                <span className="min-w-0">
                  <span className="font-semibold">{LABEL[c.type]}</span>
                  {c.instrument && (
                    <>
                      {" · "}
                      <Link href={`/holdings/${encodeURIComponent(c.instrument.code)}`} className="text-muted hover:text-foreground hover:underline">
                        {c.instrument.name}
                      </Link>
                    </>
                  )}
                </span>
                <span className={`shrink-0 font-semibold tabular-nums ${c.type === "DIVIDEND" ? "text-gain" : ""}`}>
                  {out ? "−" : "+"}
                  {formatWon(Math.abs(c.amount))}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
