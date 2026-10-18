import Link from "next/link";
import type { DividendsInsight } from "@/lib/api/portfolio-types";
import { formatWon } from "@/lib/format";

/** 연도별 합계. 마지막 해가 12월 전에 끝나면 몇 월까지인지 붙인다. */
export function DividendYears({ byYear, lastMonth }: { byYear: DividendsInsight["byYear"]; lastMonth: string }) {
  const max = Math.max(1, ...byYear.map((y) => y.amount));
  const lastYear = Number(lastMonth.slice(0, 4));
  const lastMm = Number(lastMonth.slice(5, 7));
  return (
    <section aria-labelledby="div-years" className="space-y-3">
      <h2 id="div-years" className="text-lg font-semibold">
        연도별
      </h2>
      <dl className="space-y-2 rounded-2xl border border-border p-4 text-sm tabular-nums">
        {byYear.map((y) => (
          <div key={y.year} className="grid grid-cols-[5.5rem_minmax(0,1fr)_auto] items-center gap-3">
            <dt>
              {y.year}
              {y.year === lastYear && lastMm < 12 && <span className="text-xs text-muted"> · {lastMm}월까지</span>}
            </dt>
            <dd aria-hidden className="h-2.5 rounded-sm bg-surface">
              <span className="block h-full rounded-sm bg-series-lump" style={{ width: `${(y.amount / max) * 100}%` }} />
            </dd>
            <dd className="text-right">{formatWon(y.amount)}</dd>
          </div>
        ))}
      </dl>
    </section>
  );
}

/** 종목별 합계. 금액 큰 순(응답 순서 그대로). */
export function DividendInstruments({ rows, total }: { rows: DividendsInsight["byInstrument"]; total: number }) {
  return (
    <section aria-labelledby="div-instruments" className="space-y-3">
      <h2 id="div-instruments" className="text-lg font-semibold">
        종목별
      </h2>
      <div className="overflow-hidden rounded-2xl border border-border">
        <table className="w-full text-sm tabular-nums">
          <thead className="bg-surface text-xs text-muted">
            <tr>
              <th scope="col" className="px-3 py-2 text-left font-medium">
                종목
              </th>
              <th scope="col" className="px-3 py-2 text-right font-medium">
                횟수
              </th>
              <th scope="col" className="px-3 py-2 text-right font-medium">
                금액
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.instrument.code} className="border-t border-border">
                <th scope="row" className="px-3 py-2 text-left font-normal break-keep">
                  <Link href={`/holdings/${encodeURIComponent(r.instrument.code)}`} className="underline-offset-2 hover:underline">
                    {r.instrument.name}
                  </Link>
                  {r.instrument.market === "US" && <span className="text-xs text-muted"> · 해외</span>}
                </th>
                <td className="px-3 py-2 text-right text-muted">{r.count}회</td>
                <td className="px-3 py-2 text-right">{formatWon(r.amount)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t border-border bg-surface font-semibold">
              <th scope="row" colSpan={2} className="px-3 py-2 text-left">
                합계
              </th>
              <td className="px-3 py-2 text-right">{formatWon(total)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    </section>
  );
}
