"use client";

import { useSearchParams } from "next/navigation";
import { WaterfallBody } from "@/components/insights/waterfall/WaterfallBody";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { getWaterfallInsight } from "@/lib/api/portfolio-client";
import type { WaterfallPeriod } from "@/lib/api/portfolio-types";
import { useApi } from "@/lib/use-api";

const PERIOD_OPTIONS: { value: WaterfallPeriod; label: string }[] = [
  { value: "1M", label: "1개월" },
  { value: "3M", label: "3개월" },
  { value: "YTD", label: "올해" },
  { value: "1Y", label: "1년" },
  { value: "ALL", label: "전체" },
];

function parsePeriod(value: string | null): WaterfallPeriod {
  return PERIOD_OPTIONS.find((o) => o.value === value)?.value ?? "YTD";
}

export function WaterfallView() {
  const searchParams = useSearchParams();
  const period = parsePeriod(searchParams.get("period"));
  const { state, retry } = useApi(`waterfall:${period}`, (signal) => getWaterfallInsight(period, signal));

  const choose = (next: WaterfallPeriod) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("period", next);
    window.history.replaceState(null, "", `?${params}`);
  };

  return (
    <div className="space-y-6">
      <SegmentedControl legend="기간" hideLegend options={PERIOD_OPTIONS} value={period} onChange={choose} />
      {state.status === "loading" && <LoadingBlock height={520} />}
      {state.status === "error" && <ErrorBlock error={state.error} onRetry={retry} />}
      {state.status === "success" && <WaterfallBody data={state.data} />}
    </div>
  );
}
