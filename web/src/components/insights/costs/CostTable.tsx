import Link from "next/link";
import type { CostsInsight } from "@/lib/api/portfolio-types";
import { formatWon } from "@/lib/format";
import { holdingHref } from "@/lib/routes";

/** 종목별 비용. 비용 큰 순(응답 순서 그대로). 체결 횟수와 회당 비용은 종목 이름 아래에 적는다. */
export function CostTable({ rows, total }: { rows: CostsInsight["byInstrument"]; total: CostsInsight["total"] }) {
  const trades = rows.reduce((s, r) => s + r.trades, 0);
  return (
    <section aria-labelledby="cost-instruments" className="space-y-3">
      <h2 id="cost-instruments" className="text-lg font-semibold">
        종목별
      </h2>
      <div className="overflow-hidden rounded-2xl border border-border">
        <table className="w-full text-sm tabular-nums">
          <thead className="bg-surface text-xs text-muted">
            <tr>
              <th scope="col" className="px-3 py-2 text-left font-medium">
                종목
              </th>
              <th scope="col" className="px-2 py-2 text-right font-medium">
                수수료
              </th>
              <th scope="col" className="px-2 py-2 text-right font-medium">
                세금
              </th>
              <th scope="col" className="px-3 py-2 text-right font-medium">
                합계
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => {
              const cost = r.fee + r.tax;
              return (
                <tr key={r.instrument.code} className="border-t border-border align-top">
                  <th scope="row" className="px-3 py-2 text-left font-normal break-keep">
                    <Link href={holdingHref(r.instrument.code)} className="underline-offset-2 hover:underline">
                      {r.instrument.name}
                    </Link>
                    <span className="block text-xs text-muted">
                      체결 {r.trades}회 · 회당 {formatWon(r.trades ? cost / r.trades : 0)}
                    </span>
                  </th>
                  <td className="px-2 py-2 text-right text-xs sm:text-sm">{formatWon(r.fee)}</td>
                  <td className="px-2 py-2 text-right text-xs sm:text-sm">{formatWon(r.tax)}</td>
                  <td className="px-3 py-2 text-right text-xs font-medium sm:text-sm">{formatWon(cost)}</td>
                </tr>
              );
            })}
          </tbody>
          <tfoot>
            <tr className="border-t border-border bg-surface font-semibold align-top">
              <th scope="row" className="px-3 py-2 text-left">
                합계
                <span className="block text-xs font-normal text-muted">체결 {trades}회</span>
              </th>
              <td className="px-2 py-2 text-right text-xs sm:text-sm">{formatWon(total.fee)}</td>
              <td className="px-2 py-2 text-right text-xs sm:text-sm">{formatWon(total.tax)}</td>
              <td className="px-3 py-2 text-right text-xs sm:text-sm">{formatWon(total.cost)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </section>
  );
}
