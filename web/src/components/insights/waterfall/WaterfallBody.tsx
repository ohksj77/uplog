import Link from "next/link";
import { Signed } from "@/components/result/Signed";
import { buildSteps, KIND_LABEL, type WaterfallItem } from "@/components/insights/waterfall/steps";
import { WaterfallChart } from "@/components/insights/waterfall/WaterfallChart";
import type { WaterfallInsight } from "@/lib/api/portfolio-types";
import { formatDate, formatSignedWon, formatWon, formatWonApprox } from "@/lib/format";
import { holdingHref } from "@/lib/routes";


export function WaterfallBody({ data }: { data: WaterfallInsight }) {
  const steps = buildSteps(data.items, data.total);

  return (
    <div className="space-y-8">
      <Headline data={data} />
      <Equation data={data} />

      <section aria-labelledby="wf-chart" className="space-y-3">
        <div>
          <h2 id="wf-chart" className="text-lg font-semibold">
            손익 폭포수
          </h2>
          <p className="mt-1 text-xs text-muted">
            0에서 시작해 항목마다 더하고 빼요. 종목은 주가 효과만, 해외 종목의 환율 효과는 &lsquo;환율&rsquo; 한 줄에 모았어요.
          </p>
        </div>
        <WaterfallChart steps={steps} title={`${formatDate(data.from)} ~ ${formatDate(data.to)} 손익 기여`} />
      </section>

      <ItemTable items={data.items} total={data.total} />
    </div>
  );
}

function Headline({ data }: { data: WaterfallInsight }) {
  const ranked = data.items.filter((it) => it.kind !== "OTHER");
  const best = ranked.reduce<WaterfallItem | null>((m, it) => (it.amount > 0 && (!m || it.amount > m.amount) ? it : m), null);
  const worst = ranked.reduce<WaterfallItem | null>((m, it) => (it.amount < 0 && (!m || it.amount < m.amount) ? it : m), null);
  const verb = data.total > 0 ? "벌었어요" : data.total < 0 ? "잃었어요" : "본전이에요";

  return (
    <section aria-label="요약" className="rounded-2xl bg-surface p-5">
      <p className="text-lg leading-snug font-semibold break-keep">
        이 기간에{" "}
        <Signed value={data.total}>{data.total === 0 ? "" : formatWonApprox(Math.abs(data.total))}</Signed> {verb}.
      </p>
      <p className="mt-2 text-sm text-muted break-keep">
        {best && (
          <>
            가장 크게 벌어 준 건 <span className="font-medium text-foreground">{best.label}</span>(
            <span className="text-gain">{formatSignedWon(best.amount)}</span>)
          </>
        )}
        {best && worst && ", "}
        {worst && (
          <>
            가장 크게 깎은 건 <span className="font-medium text-foreground">{worst.label}</span>(
            <span className="text-loss">{formatSignedWon(worst.amount)}</span>)
          </>
        )}
        {(best || worst) && "이에요."}
      </p>
    </section>
  );
}

/** 시작 평가금 + 순입금 + 기간 손익 = 끝 평가금 */
function Equation({ data }: { data: WaterfallInsight }) {
  const rows = [
    { op: "", label: "시작 평가금", note: formatDate(data.from), value: formatWon(data.startValue) },
    { op: "+", label: "순입금", note: "입금 − 출금", value: formatSignedWon(data.netCashFlow) },
    { op: "+", label: "기간 손익", note: "아래 항목의 합", value: formatSignedWon(data.total), tone: data.total },
    { op: "=", label: "끝 평가금", note: formatDate(data.to), value: formatWon(data.endValue) },
  ];
  return (
    <dl aria-label="평가금 변화" className="rounded-2xl border border-border p-4 text-sm tabular-nums">
      {rows.map((r) => (
        <div key={r.label} className={`flex items-baseline gap-2 py-1 ${r.op === "=" ? "mt-1 border-t border-border pt-2 font-semibold" : ""}`}>
          <span aria-hidden className="w-3 text-muted">
            {r.op}
          </span>
          <dt className="min-w-0 flex-1">
            {r.label} <span className="text-xs font-normal text-muted">{r.note}</span>
          </dt>
          <dd className={r.tone == null ? "" : r.tone > 0 ? "text-gain" : r.tone < 0 ? "text-loss" : ""}>{r.value}</dd>
        </div>
      ))}
    </dl>
  );
}

function ItemTable({ items, total }: { items: WaterfallItem[]; total: number }) {
  return (
    <section aria-labelledby="wf-table" className="space-y-3">
      <h2 id="wf-table" className="text-lg font-semibold">
        모든 항목
      </h2>
      <div className="overflow-hidden rounded-2xl border border-border">
        <table className="w-full text-sm tabular-nums">
          <thead className="bg-surface text-xs text-muted">
            <tr>
              <th scope="col" className="px-3 py-2 text-left font-medium">
                항목
              </th>
              <th scope="col" className="px-3 py-2 text-left font-medium">
                구분
              </th>
              <th scope="col" className="px-3 py-2 text-right font-medium">
                금액
              </th>
            </tr>
          </thead>
          <tbody>
            {items.map((it, i) => (
              <tr key={`${it.kind}-${it.instrument?.code ?? i}`} className="border-t border-border">
                <th scope="row" className="px-3 py-2 text-left font-normal break-keep">
                  {it.instrument ? (
                    <Link href={holdingHref(it.instrument.code)} className="underline-offset-2 hover:underline">
                      {it.label}
                    </Link>
                  ) : (
                    it.label
                  )}
                </th>
                <td className="px-3 py-2 text-muted">
                  {KIND_LABEL[it.kind]}
                  {it.instrument?.market === "US" && " · 해외"}
                </td>
                <td className="px-3 py-2 text-right">
                  <Signed value={it.amount}>{formatSignedWon(it.amount)}</Signed>
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t border-border bg-surface font-semibold">
              <th scope="row" colSpan={2} className="px-3 py-2 text-left">
                합계
              </th>
              <td className="px-3 py-2 text-right">
                <Signed value={total}>{formatSignedWon(total)}</Signed>
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
    </section>
  );
}
