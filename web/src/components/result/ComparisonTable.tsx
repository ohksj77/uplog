import { SampleBadge } from "@/components/result/SampleBadge";
import { STRATEGIES, STRATEGY_LABEL } from "@/components/result/text";
import { Signed } from "@/components/result/Signed";
import type { SimulationResponse } from "@/lib/api/types";
import { formatPercent, formatWonShort } from "@/lib/format";

type ComparisonTableProps = {
  results: SimulationResponse["results"];
  selected: number;
};

/** ETF 여러 개일 때 한눈에 보는 비교표. 칸마다 최종 평가금액과 총 수익률. */
export function ComparisonTable({ results, selected }: ComparisonTableProps) {
  return (
    <section aria-labelledby="compare-heading" className="rounded-2xl bg-surface p-5">
      <h2 id="compare-heading" className="mb-3 text-base font-semibold">
        ETF 비교
      </h2>
      <table className="w-full table-fixed text-sm">
        <caption className="sr-only">ETF별 최종 평가금액과 총 수익률</caption>
        <thead>
          <tr className="text-left text-xs text-muted">
            <th scope="col" className="w-[40%] pb-2 font-medium">
              ETF
            </th>
            {STRATEGIES.map((s) => (
              <th key={s} scope="col" className="pb-2 text-right font-medium">
                {STRATEGY_LABEL[s]}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {results.map((r, i) => (
            <tr key={r.etf.code} className="border-t border-border">
              <th scope="row" className="py-3 pr-2 text-left align-top font-medium">
                <span className={`block break-keep ${i === selected ? "text-foreground" : "text-muted"}`}>
                  {r.etf.name}
                </span>
                {r.etf.sample && (
                  <span className="mt-1 block">
                    <SampleBadge />
                  </span>
                )}
              </th>
              {STRATEGIES.map((s) => (
                <td key={s} className="py-3 text-right align-top tabular-nums">
                  <span className="block font-medium">{formatWonShort(r[s].summary.finalValue)}원</span>
                  <Signed value={r[s].summary.totalReturn} className="block text-xs">
                    {formatPercent(r[s].summary.totalReturn)}
                  </Signed>
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
