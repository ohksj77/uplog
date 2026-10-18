"use client";

import { EffectBars, EffectLegend } from "@/components/insights/fx/EffectBars";
import { FxItemCard } from "@/components/insights/fx/FxItemCard";
import { Signed } from "@/components/result/Signed";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { getFxInsight } from "@/lib/api/portfolio-client";
import type { FxInsight } from "@/lib/api/portfolio-types";
import { formatDate, formatSignedWon } from "@/lib/format";
import { useApi } from "@/lib/use-api";

export function FxView() {
  const { state, retry } = useApi("fx", (signal) => Promise.resolve().then(() => getFxInsight(signal)));
  if (state.status === "loading") return <LoadingBlock height={420} />;
  if (state.status === "error") return <ErrorBlock error={state.error} onRetry={retry} />;
  return <Body data={state.data} />;
}

/** 손익 크기 중 주가·환율이 차지하는 몫을 사실대로 한 줄로 */
export function fxSentence(priceEffect: number, fxEffect: number): string {
  const sum = Math.abs(priceEffect) + Math.abs(fxEffect);
  if (sum === 0) return "주가와 환율 모두 손익을 만들지 않았어요.";
  const priceShare = Math.round((Math.abs(priceEffect) / sum) * 100);
  const main = priceShare >= 50 ? "주가" : "환율";
  const share = priceShare >= 50 ? priceShare : 100 - priceShare;
  const opposite = priceEffect !== 0 && fxEffect !== 0 && Math.sign(priceEffect) !== Math.sign(fxEffect);
  return `손익 크기의 ${share}%가 ${main}에서 나왔어요.${opposite ? " 주가와 환율이 서로 반대 방향으로 움직였어요." : ""}`;
}

function Body({ data }: { data: FxInsight }) {
  const { items, total } = data;
  if (items.length === 0) {
    return (
      <section className="rounded-2xl bg-surface px-5 py-10 text-center">
        <p className="font-semibold">지금 보유 중인 해외 종목이 없어요</p>
        <p className="mt-2 text-sm text-muted">해외 종목을 보유하면 수익을 주가 효과와 환율 효과로 나눠 보여 드려요.</p>
      </section>
    );
  }
  const max = Math.max(...items.flatMap((i) => [Math.abs(i.priceEffect), Math.abs(i.fxEffect)]));
  const totalMax = Math.max(Math.abs(total.priceEffect), Math.abs(total.fxEffect));

  return (
    <div className="space-y-6">
      <section aria-labelledby="fx-total" className="rounded-2xl bg-surface p-4">
        <div className="mb-1 flex items-baseline justify-between gap-3">
          <h2 id="fx-total" className="font-semibold">
            해외 종목 합계
          </h2>
          <Signed value={total.profit} className="text-lg font-bold tabular-nums">
            {formatSignedWon(total.profit)}
          </Signed>
        </div>
        <p className="mb-3 text-sm">{fxSentence(total.priceEffect, total.fxEffect)}</p>
        <EffectBars priceEffect={total.priceEffect} fxEffect={total.fxEffect} max={totalMax} />
        <p className="mt-3 text-xs text-muted">{formatDate(data.asOf)} 종가·환율 기준, 지금 보유 중인 수량만 계산해요.</p>
      </section>

      <section aria-labelledby="fx-items" className="space-y-3">
        <h2 id="fx-items" className="text-lg font-semibold">
          종목별
        </h2>
        <EffectLegend />
        <ul className="grid gap-3 md:grid-cols-2">
          {items.map((item) => (
            <li key={item.instrument.code}>
              <FxItemCard item={item} max={max} />
            </li>
          ))}
        </ul>
        <p className="text-xs text-muted">
          주가 효과 = 수량 × (현재가 − 평균 단가) × 매수 평균 환율, 환율 효과 = 수량 × 현재가 × (현재 환율 − 매수 평균 환율). 둘을 더하면 평가 손익이에요.
        </p>
      </section>
    </div>
  );
}
