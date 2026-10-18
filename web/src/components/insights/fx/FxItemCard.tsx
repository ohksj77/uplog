import Link from "next/link";
import { EffectBars } from "@/components/insights/fx/EffectBars";
import { formatFx, formatLocal } from "@/components/holding/format";
import { Signed } from "@/components/result/Signed";
import type { FxItem } from "@/lib/api/portfolio-types";
import { formatPercent, formatSignedWon } from "@/lib/format";
import { holdingHref } from "@/lib/routes";

// 해외 종목 하나의 수익 분해.

export function FxItemCard({ item, max, showInstrument = true }: { item: FxItem; max: number; showInstrument?: boolean }) {
  const { instrument: inst } = item;
  const priceChange = item.avgCostLocal ? item.priceLocal / item.avgCostLocal - 1 : 0;
  const fxChange = item.avgFxRate ? item.fxRate / item.avgFxRate - 1 : 0;
  return (
    <article className="rounded-2xl bg-surface p-4" aria-label={`${inst.name} 수익 분해`}>
      <header className="mb-3 flex items-baseline justify-between gap-3">
        {showInstrument ? (
          <Link href={holdingHref(inst.code)} className="min-w-0 truncate font-semibold hover:underline">
            {inst.name}
          </Link>
        ) : (
          <span className="text-sm text-muted">평가 손익</span>
        )}
        <Signed value={item.profit} className="shrink-0 font-semibold tabular-nums">
          {formatSignedWon(item.profit)}
        </Signed>
      </header>
      <EffectBars priceEffect={item.priceEffect} fxEffect={item.fxEffect} max={max} />
      <dl className="mt-3 grid grid-cols-1 gap-1 text-xs text-muted sm:grid-cols-2">
        <div>
          <dt className="inline">주가 </dt>
          <dd className="inline tabular-nums">
            평균 {formatLocal(item.avgCostLocal, inst.currency)} → 지금 {formatLocal(item.priceLocal, inst.currency)} ({formatPercent(priceChange, 1)})
          </dd>
        </div>
        <div>
          <dt className="inline">환율 </dt>
          <dd className="inline tabular-nums">
            매수 평균 {formatFx(item.avgFxRate)} → 지금 {formatFx(item.fxRate)} ({formatPercent(fxChange, 1)})
          </dd>
        </div>
        <div>
          <dt className="inline">수량 </dt>
          <dd className="inline tabular-nums">{item.quantity.toLocaleString("ko-KR")}주</dd>
        </div>
      </dl>
    </article>
  );
}
