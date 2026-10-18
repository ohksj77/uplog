"use client";

import { useState } from "react";
import type { LogMode } from "@/components/log-grid/types";
import { LogSection } from "@/components/result/LogSection";
import { PainMetrics } from "@/components/result/PainMetrics";
import { SampleBadge } from "@/components/result/SampleBadge";
import { StreakMetrics } from "@/components/result/StreakMetrics";
import { SummaryCards } from "@/components/result/SummaryCards";
import { ValueChart } from "@/components/result/ValueChart";
import type { SimulationResponse } from "@/lib/api/types";

type EtfResultProps = {
  result: SimulationResponse["results"][number];
  mode: LogMode;
  onModeChange: (mode: LogMode) => void;
};

/** ETF 하나의 결과. 고른 날은 로그 두 줄과 차트가 함께 쓴다(ETF가 바뀌면 key로 초기화). */
export function EtfResult({ result, mode, onModeChange }: EtfResultProps) {
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const { etf, lumpSum, recurring } = result;
  const results = { lumpSum, recurring };

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center gap-2">
        <p className="text-base font-semibold break-keep">{etf.name}</p>
        <span className="text-xs text-muted">{etf.code}</span>
        {etf.sample && <SampleBadge />}
      </div>
      <LogSection
        etfName={etf.name}
        results={results}
        mode={mode}
        onModeChange={onModeChange}
        selectedDate={selectedDate}
        onSelectDate={setSelectedDate}
      />
      <StreakMetrics results={results} />
      <ValueChart etfName={etf.name} results={results} selectedDate={selectedDate} onSelectDate={setSelectedDate} />
      <SummaryCards results={results} />
      <PainMetrics results={results} />
    </div>
  );
}
