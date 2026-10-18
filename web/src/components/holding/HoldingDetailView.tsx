"use client";

import Link from "next/link";
import type { ReactNode } from "react";
import { MARKET_LABEL } from "@/components/holding/format";
import { PositionCard } from "@/components/holding/PositionCard";
import { PriceHistory } from "@/components/holding/PriceHistory";
import { TradeList } from "@/components/holding/TradeList";
import { FxItemCard, EffectLegend } from "@/components/insights/fx";
import { PeriodList, ReturnLegend, Timeline } from "@/components/insights/holding-periods";
import { SaleCard } from "@/components/insights/sales";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { PageHeader } from "@/components/ui/PageHeader";
import { getHoldingDetail } from "@/lib/api/portfolio-client";
import type { HoldingDetail } from "@/lib/api/portfolio-types";
import { useApi } from "@/lib/use-api";
import { MockBadge } from "@/components/holding/MockBadge";

// 종목 상세 본체. 보유 현황 → 주가와 체결 → 보유 기간 → 매도 판정 → 수익 분해 → 체결 내역.

const BACK = { href: "/", label: "수익 로그" };

export function HoldingDetailView({ code }: { code: string }) {
  // mock이 동기로 던져도 거절된 Promise가 되게 한 번 감싼다
  const { state, retry } = useApi(`holding:${code}`, (signal) => Promise.resolve().then(() => getHoldingDetail(code, signal)));

  if (state.status === "loading") {
    return (
      <div>
        <PageHeader title="종목 상세" back={BACK} />
        <LoadingBlock height={520} />
      </div>
    );
  }
  if (state.status === "error") {
    const notFound = state.error.status === 404 && state.error.code !== "ACCOUNT_NOT_CONNECTED";
    return (
      <div>
        <PageHeader title="종목 상세" back={BACK} />
        {notFound ? <NotFound code={code} /> : <ErrorBlock error={state.error} onRetry={retry} />}
      </div>
    );
  }
  return <Body detail={state.data} />;
}

function NotFound({ code }: { code: string }) {
  return (
    <section role="alert" className="rounded-2xl bg-surface px-5 py-10 text-center">
      <p className="font-semibold">이 종목은 거래한 기록이 없어요</p>
      <p className="mt-2 text-sm text-muted">종목 코드 {code}로 보유하거나 사고판 이력을 찾지 못했어요.</p>
      <Link href="/" className="mt-6 inline-flex h-11 items-center rounded-xl bg-accent px-5 text-sm font-semibold text-white">
        수익 로그로 가기
      </Link>
    </section>
  );
}

function Body({ detail }: { detail: HoldingDetail }) {
  const { instrument: inst, prices, periods, sales, fx } = detail;
  const from = prices[0]?.date ?? periods[0]?.open;
  const to = prices[prices.length - 1]?.date;

  return (
    <div>
      <PageHeader
        title={inst.name}
        question="이 종목을 언제 사고팔았고, 결과는 어땠나"
        back={BACK}
        aside={
          <span className="flex items-center gap-2 text-sm text-muted">
            {inst.code} · {MARKET_LABEL[inst.market]}
            <MockBadge />
          </span>
        }
      />
      <div className="space-y-10">
        <PositionCard detail={detail} />

        <Section id="price" title="주가와 체결">
          <PriceHistory detail={detail} />
        </Section>

        {periods.length > 0 && from && to && (
          <Section id="periods" title="보유 기간" more={{ href: "/insights/holding-periods", label: "전체 타임라인" }}>
            <ReturnLegend />
            <Timeline periods={periods} from={from} to={to} link={false} minWidth={0} showNames={false} />
            <PeriodList periods={periods} showInstrument={false} />
          </Section>
        )}

        {sales.length > 0 && (
          <Section id="sales" title="매도 판정" more={{ href: "/insights/sales", label: "모든 매도" }}>
            <p className="text-sm text-muted">매도 20거래일 뒤 종가가 매도가 이하면 판 게 나았다고 봐요.</p>
            <ul className="grid gap-3 md:grid-cols-2">
              {[...sales].reverse().map((s) => (
                <li key={s.id}>
                  <SaleCard sale={s} horizon={20} showInstrument={false} />
                </li>
              ))}
            </ul>
          </Section>
        )}

        {fx && (
          <Section id="fx" title="수익 분해" more={{ href: "/insights/fx", label: "해외 종목 전체" }}>
            <EffectLegend />
            <FxItemCard item={fx} max={Math.max(Math.abs(fx.priceEffect), Math.abs(fx.fxEffect))} showInstrument={false} />
          </Section>
        )}

        <Section id="trades" title={`체결 내역 ${detail.trades.length}건`}>
          <TradeList trades={detail.trades} />
        </Section>
      </div>
    </div>
  );
}

function Section({
  id,
  title,
  more,
  children,
}: {
  id: string;
  title: string;
  more?: { href: string; label: string };
  children: ReactNode;
}) {
  return (
    <section aria-labelledby={`h-${id}`} className="space-y-3">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id={`h-${id}`} className="text-lg font-semibold">
          {title}
        </h2>
        {more && (
          <Link href={more.href} className="shrink-0 text-sm text-muted hover:text-foreground">
            {more.label} →
          </Link>
        )}
      </div>
      {children}
    </section>
  );
}
