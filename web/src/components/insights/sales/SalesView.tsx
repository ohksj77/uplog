"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { TrailLegend } from "@/components/holding/TrailLegend";
import { SaleCard } from "@/components/insights/sales/SaleCard";
import { SalesSummary } from "@/components/insights/sales/SalesSummary";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { getSalesInsight } from "@/lib/api/portfolio-client";
import type { SaleHorizon, SalesInsight } from "@/lib/api/portfolio-types";
import { useApi } from "@/lib/use-api";

// 판 뒤의 그림자 본체. 판정 기준(거래일 수)은 URL ?horizon= 에 둔다.

type Filter = "ALL" | "GOOD" | "BAD";

const HORIZON_OPTIONS = [
  { value: "5", label: "5거래일" },
  { value: "20", label: "20거래일" },
  { value: "60", label: "60거래일" },
] as const;

const FILTER_OPTIONS: { value: Filter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "GOOD", label: "잘한 매도" },
  { value: "BAD", label: "아쉬운 매도" },
];

function parseHorizon(raw: string | null): SaleHorizon {
  return raw === "5" ? 5 : raw === "60" ? 60 : 20;
}

export function SalesView() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const horizon = parseHorizon(searchParams.get("horizon"));
  const [filter, setFilter] = useState<Filter>("ALL");
  const { state, retry } = useApi(`sales:${horizon}`, (signal) => Promise.resolve().then(() => getSalesInsight(horizon, signal)));

  const setHorizon = (value: string) => {
    const q = new URLSearchParams(searchParams.toString());
    q.set("horizon", value);
    router.replace(`${pathname}?${q}`, { scroll: false });
  };

  return (
    <div className="space-y-6">
      <SegmentedControl legend="판정 기준: 매도 뒤" options={[...HORIZON_OPTIONS]} value={String(horizon)} onChange={setHorizon} />
      {state.status === "loading" && <LoadingBlock height={420} />}
      {state.status === "error" && <ErrorBlock error={state.error} onRetry={retry} />}
      {state.status === "success" && <SalesBody data={state.data} filter={filter} onFilter={setFilter} />}
    </div>
  );
}

function SalesBody({ data, filter, onFilter }: { data: SalesInsight; filter: Filter; onFilter: (f: Filter) => void }) {
  if (data.sales.length === 0) {
    return (
      <section className="rounded-2xl bg-surface px-5 py-10 text-center">
        <p className="font-semibold">아직 매도한 기록이 없어요</p>
        <p className="mt-2 text-sm text-muted">종목을 팔면 그 뒤 주가를 이어 그려 드려요.</p>
      </section>
    );
  }
  const shown = filter === "ALL" ? data.sales : data.sales.filter((s) => s.verdict === filter);
  return (
    <>
      <SalesSummary data={data} />
      <section aria-labelledby="sales-list" className="space-y-3">
        <h2 id="sales-list" className="text-lg font-semibold">
          매도 기록
        </h2>
        <SegmentedControl legend="매도 기록 거르기" hideLegend options={FILTER_OPTIONS} value={filter} onChange={onFilter} />
        <TrailLegend items={["solid", "dashed", "ref", "guide", "sell"]} labels={{ solid: "매도 전" }} />
        {shown.length === 0 ? (
          <p className="rounded-2xl bg-surface px-5 py-8 text-center text-sm text-muted">이 조건에 맞는 매도가 없어요.</p>
        ) : (
          <ul className="grid gap-3 md:grid-cols-2">
            {shown.map((s) => (
              <li key={s.id}>
                <SaleCard sale={s} horizon={data.horizon} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
