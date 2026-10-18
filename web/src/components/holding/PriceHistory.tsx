"use client";

import { formatLocal } from "@/components/holding/format";
import { TrailChart, type TrailMarker, type TrailRef } from "@/components/holding/TrailChart";
import { TrailLegend } from "@/components/holding/TrailLegend";
import type { HoldingDetail } from "@/lib/api/portfolio-types";
import { formatDate, formatPercent } from "@/lib/format";

// 종목 상세의 주가 차트. 보유 중인 구간은 실선, 보유하지 않은 구간(매도 뒤 포함)은 점선.
// 매도마다 매도가 기준선을 판정 기준(20거래일)까지 긋는다.

const REF_DAYS = 20;

export function PriceHistory({ detail }: { detail: HoldingDetail }) {
  const { prices, instrument: inst } = detail;
  const trades = [...detail.trades].sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
  const index = new Map(prices.map((p, i) => [p.date, i]));
  const fmt = (v: number) => formatLocal(v, inst.currency);

  // 그날 체결을 반영한 뒤 보유 중이면 다음 날까지의 선을 실선으로
  const solid: boolean[] = [];
  let qty = 0;
  let t = 0;
  for (const p of prices) {
    while (t < trades.length && trades[t].date <= p.date) {
      qty += trades[t].side === "BUY" ? trades[t].quantity : -trades[t].quantity;
      t += 1;
    }
    solid.push(qty > 0);
  }

  const markers: TrailMarker[] = trades.flatMap((tr) => {
    const i = index.get(tr.date);
    return i == null ? [] : [{ index: i, side: tr.side }];
  });
  const refs: TrailRef[] = detail.sales.flatMap((s) => {
    const i = index.get(s.date);
    return i == null ? [] : [{ fromIndex: i, toIndex: i + REF_DAYS, value: s.priceLocal }];
  });

  const first = prices[0];
  const last = prices[prices.length - 1];
  const buys = trades.filter((x) => x.side === "BUY").length;
  const description = first
    ? `${formatDate(first.date)} ${fmt(first.closeLocal)}에서 ${formatDate(last.date)} ${fmt(last.closeLocal)}(${formatPercent(
        last.closeLocal / first.closeLocal - 1,
        1,
      )}). 매수 ${buys}번, 매도 ${trades.length - buys}번.`
    : "가격 기록이 없어요.";

  const tradesOn = (i: number) => {
    const date = prices[i]?.date;
    const today = trades.filter((x) => x.date === date);
    return today.length ? today.map((x) => `${x.side === "BUY" ? "매수" : "매도"} ${x.quantity}주`).join(", ") : null;
  };

  if (prices.length === 0) return <p className="text-sm text-muted">가격 기록이 없어요.</p>;

  return (
    <div className="space-y-3">
      <TrailLegend items={["solid", "dashed", "buy", "sell", "ref"]} labels={{ dashed: "보유하지 않음", ref: `매도가(${REF_DAYS}거래일)` }} />
      <TrailChart
        points={prices}
        solid={solid}
        markers={markers}
        refs={refs}
        formatY={fmt}
        title={`${inst.name} 주가`}
        description={description}
        height={240}
        describeIndex={tradesOn}
      />
    </div>
  );
}
