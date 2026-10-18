import Link from "next/link";
import { formatLocal } from "@/components/holding/format";
import { TrailChart, solidUntil } from "@/components/holding/TrailChart";
import { Signed } from "@/components/result/Signed";
import { VerdictChip } from "@/components/insights/sales/VerdictChip";
import type { Sale, SaleHorizon } from "@/lib/api/portfolio-types";
import { formatDate, formatPercent, formatSignedWon, formatWon } from "@/lib/format";

// 매도 한 건: 매도 전 실선, 매도 뒤 점선, 매도가 기준선, 판정.

const HORIZONS: SaleHorizon[] = [5, 20, 60];

export function SaleCard({ sale, horizon, showInstrument = true }: { sale: Sale; horizon: SaleHorizon; showInstrument?: boolean }) {
  const { instrument: inst } = sale;
  const at = Math.max(0, sale.series.findIndex((p) => p.date === sale.date));
  const fmt = (v: number) => formatLocal(v, inst.currency);
  const atHorizon = sale.changeAfter[`d${horizon}`];

  return (
    <article className="rounded-2xl bg-surface p-4">
      <header className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          {showInstrument && (
            <Link href={`/holdings/${inst.code}`} className="block truncate font-semibold hover:underline">
              {inst.name}
            </Link>
          )}
          <p className={showInstrument ? "text-xs text-muted" : "text-sm font-medium"}>
            {formatDate(sale.date)} 매도 · {sale.quantity.toLocaleString("ko-KR")}주 × {fmt(sale.priceLocal)}
          </p>
        </div>
        <VerdictChip verdict={sale.verdict} />
      </header>

      <div className="mt-3">
        <TrailChart
          compact
          height={104}
          points={sale.series}
          solid={solidUntil(sale.series.length, at)}
          markers={[{ index: at, side: "SELL" }]}
          refs={[{ fromIndex: at, toIndex: sale.series.length - 1, value: sale.priceLocal }]}
          guides={[at + horizon]}
          formatY={fmt}
          title={`${inst.name} ${formatDate(sale.date)} 매도 전후 주가`}
          description={`매도가 ${fmt(sale.priceLocal)}. ${horizon}거래일 뒤 ${
            atHorizon == null ? "아직 지나지 않음" : formatPercent(atHorizon, 1)
          }, 지금 ${fmt(sale.priceNowLocal)}(${formatPercent(sale.changeSinceSale, 1)}).`}
        />
      </div>

      <dl className="mt-3 grid grid-cols-4 gap-1 text-center text-xs">
        {HORIZONS.map((h) => {
          const v = sale.changeAfter[`d${h}`];
          return (
            <div key={h} className={`rounded-lg py-1.5 ${h === horizon ? "bg-background font-semibold" : ""}`}>
              <dt className="text-muted">{h}일 뒤</dt>
              <dd className="mt-0.5 tabular-nums">{v == null ? "아직" : formatPercent(v, 1)}</dd>
            </div>
          );
        })}
        <div className="rounded-lg py-1.5">
          <dt className="text-muted">지금</dt>
          <dd className="mt-0.5 tabular-nums">{formatPercent(sale.changeSinceSale, 1)}</dd>
        </div>
      </dl>

      <div className="mt-3 flex flex-wrap justify-between gap-x-4 gap-y-1 text-sm">
        <AfterEffect sale={sale} horizon={horizon} />
        <span className="text-muted">
          매도 {formatWon(sale.amount)} · 실현{" "}
          <Signed value={sale.realizedProfit} className="tabular-nums">
            {formatSignedWon(sale.realizedProfit)}
          </Signed>
        </span>
      </div>
    </article>
  );
}

function AfterEffect({ sale, horizon }: { sale: Sale; horizon: SaleHorizon }) {
  if (sale.verdict === "PENDING") return <span className="text-muted">매도 뒤 {horizon}거래일이 아직 안 지났어요</span>;
  const good = sale.afterEffect >= 0;
  return (
    <span>
      {good ? "피한 손실" : "놓친 이익"}{" "}
      <span className={`font-semibold tabular-nums ${good ? "text-gain" : "text-loss"}`}>{formatWon(Math.abs(sale.afterEffect))}</span>
    </span>
  );
}
