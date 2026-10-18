"use client";

import { useSearchParams } from "next/navigation";
import { ShadowBody } from "@/components/insights/shadow/ShadowBody";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { ErrorBlock, LoadingBlock } from "@/components/ui/AsyncState";
import { getShadowInsight } from "@/lib/api/portfolio-client";
import type { BenchmarkCode } from "@/lib/api/portfolio-types";
import { useApi } from "@/lib/use-api";

const BENCHMARK_OPTIONS: { value: BenchmarkCode; label: string }[] = [
  { value: "SPX", label: "S&P500" },
  { value: "KOSPI200", label: "코스피200" },
];

function parseBenchmark(value: string | null): BenchmarkCode {
  return value === "KOSPI200" ? "KOSPI200" : "SPX";
}

export function ShadowView() {
  const searchParams = useSearchParams();
  const benchmark = parseBenchmark(searchParams.get("benchmark"));
  const { state, retry } = useApi(`shadow:${benchmark}`, (signal) => getShadowInsight(benchmark, signal));

  // 기록을 쌓지 않고 주소만 바꾼다. Next 라우터가 useSearchParams와 맞춰 준다.
  const choose = (next: BenchmarkCode) => {
    const params = new URLSearchParams(searchParams.toString());
    params.set("benchmark", next);
    window.history.replaceState(null, "", `?${params}`);
  };

  return (
    <div className="space-y-6">
      <div className="max-w-xs">
        <SegmentedControl legend="비교할 지수" hideLegend options={BENCHMARK_OPTIONS} value={benchmark} onChange={choose} />
      </div>
      {state.status === "loading" && <LoadingBlock height={480} />}
      {state.status === "error" && <ErrorBlock error={state.error} onRetry={retry} />}
      {state.status === "success" && <ShadowBody data={state.data} />}
    </div>
  );
}
