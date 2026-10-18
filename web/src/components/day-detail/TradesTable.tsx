import Link from "next/link";
import type { Trade } from "@/lib/api/portfolio-types";
import { formatLocal } from "@/components/day-detail/money";
import { formatWon } from "@/lib/format";
import { holdingHref } from "@/lib/routes";

const SIDE = { BUY: "매수", SELL: "매도" } as const;

/** 그날 체결. 좁은 화면에서는 표만 가로로 스크롤한다. */
export function TradesTable({ trades }: { trades: Trade[] }) {
  return (
    <section aria-labelledby="trades-heading" className="space-y-3">
      <h2 id="trades-heading" className="text-lg font-semibold">
        거래
      </h2>
      {trades.length === 0 ? (
        <p className="rounded-2xl bg-surface px-5 py-8 text-center text-sm text-muted">이날은 거래가 없었어요</p>
      ) : (
        <div className="overflow-x-auto rounded-2xl bg-surface" tabIndex={0} role="region" aria-labelledby="trades-heading">
          <table className="w-full min-w-[520px] text-sm">
            <thead className="text-left text-xs text-muted">
              <tr className="border-b border-border">
                <th scope="col" className="px-4 py-2.5 font-normal">종목</th>
                <th scope="col" className="px-2 py-2.5 font-normal">구분</th>
                <th scope="col" className="px-2 py-2.5 text-right font-normal">수량</th>
                <th scope="col" className="px-2 py-2.5 text-right font-normal">체결가</th>
                <th scope="col" className="px-2 py-2.5 text-right font-normal">금액</th>
                <th scope="col" className="px-4 py-2.5 text-right font-normal">수수료·세금</th>
              </tr>
            </thead>
            <tbody className="tabular-nums">
              {trades.map((t, i) => (
                <tr key={`${t.instrument.code}-${t.side}-${i}`} className="border-b border-border last:border-0">
                  <th scope="row" className="px-4 py-2.5 text-left font-semibold">
                    <Link href={holdingHref(t.instrument.code)} className="hover:underline">
                      {t.instrument.name}
                    </Link>
                  </th>
                  <td className="px-2 py-2.5">{SIDE[t.side]}</td>
                  <td className="px-2 py-2.5 text-right">{t.quantity.toLocaleString("ko-KR")}주</td>
                  <td className="px-2 py-2.5 text-right">
                    {formatLocal(t.priceLocal, t.instrument.currency)}
                    {t.fxRate !== 1 && <span className="block text-xs text-muted">환율 {t.fxRate.toLocaleString("ko-KR")}</span>}
                  </td>
                  <td className="px-2 py-2.5 text-right">{formatWon(t.amount)}</td>
                  <td className="px-4 py-2.5 text-right">{formatWon(t.fee + t.tax)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
