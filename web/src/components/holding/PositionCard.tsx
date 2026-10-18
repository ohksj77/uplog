import type { ReactNode } from "react";
import { formatLocal } from "@/components/holding/format";
import { Signed } from "@/components/result/Signed";
import type { HoldingDetail } from "@/lib/api/portfolio-types";
import { formatPercent, formatSignedWon, formatWon } from "@/lib/format";

// 지금 보유분과 지금까지 확정된 손익(실현 손익·배당).

export function PositionCard({ detail }: { detail: HoldingDetail }) {
  const { position: p, instrument: inst } = detail;
  const local = (v: number) => formatLocal(v, inst.currency);
  return (
    <section aria-label="보유 현황" className="grid gap-3 sm:grid-cols-[3fr_2fr]">
      <div className="rounded-2xl bg-surface p-4">
        {p ? (
          <>
            <p className="text-sm text-muted">평가금액</p>
            <p className="mt-1 text-2xl font-bold tabular-nums">{formatWon(p.value)}</p>
            <p className="mt-1 text-sm tabular-nums">
              <Signed value={p.profit} className="font-semibold">
                {formatSignedWon(p.profit)} ({formatPercent(p.returnRate)})
              </Signed>
            </p>
            <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
              <Item label="보유 수량">{p.quantity.toLocaleString("ko-KR")}주</Item>
              <Item label="평균 단가">{local(p.avgCostLocal)}</Item>
              <Item label="현재가">{local(p.priceLocal)}</Item>
              {inst.currency === "USD" && <Item label="환율">{p.fxRate.toLocaleString("ko-KR")}원</Item>}
            </dl>
          </>
        ) : (
          <div className="py-4">
            <p className="font-semibold">지금은 보유하지 않아요</p>
            <p className="mt-1 text-sm text-muted">아래에서 지난 거래와 판 뒤의 주가를 볼 수 있어요.</p>
          </div>
        )}
      </div>
      <dl className="grid grid-cols-2 gap-3 sm:grid-cols-1">
        <div className="rounded-2xl bg-surface p-4">
          <dt className="text-sm text-muted">실현 손익</dt>
          <dd className="mt-1 font-semibold tabular-nums">
            <Signed value={detail.realizedProfit}>{formatSignedWon(detail.realizedProfit)}</Signed>
          </dd>
        </div>
        {detail.dividends > 0 && (
          <div className="rounded-2xl bg-surface p-4">
            <dt className="text-sm text-muted">받은 배당</dt>
            <dd className="mt-1 font-semibold tabular-nums">{formatWon(detail.dividends)}</dd>
          </div>
        )}
      </dl>
    </section>
  );
}

function Item({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex justify-between gap-2">
      <dt className="text-muted">{label}</dt>
      <dd className="tabular-nums">{children}</dd>
    </div>
  );
}
