"use client";

import { useState } from "react";
import type { LogMode } from "@/components/log-grid/types";
import { LogSection } from "@/components/result/LogSection";
import { PainMetrics } from "@/components/result/PainMetrics";
import { InstrumentBadges } from "@/components/etf-picker";
import { StreakMetrics } from "@/components/result/StreakMetrics";
import { SummaryCards } from "@/components/result/SummaryCards";
import { ValueChart } from "@/components/result/ValueChart";
import type { Currency, SimulationResponse } from "@/lib/api/types";

type EtfResultProps = {
  result: SimulationResponse["results"][number];
  currency: Currency;
  mode: LogMode;
  onModeChange: (mode: LogMode) => void;
};

/** 종목 하나의 결과. 고른 날은 로그 두 줄과 차트가 함께 쓴다(종목이 바뀌면 key로 초기화). */
export function EtfResult({ result, currency, mode, onModeChange }: EtfResultProps) {
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const { etf, lumpSum, recurring } = result;
  const results = { lumpSum, recurring };

  return (
    <div className="space-y-8">
      <div className="space-y-1.5">
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-base font-semibold break-keep">{etf.name}</p>
          <span className="text-xs text-muted">{etf.code}</span>
          <InstrumentBadges etf={etf} />
        </div>
        {etf.currency === "USD" && currency === "KRW" && (
          <p className="text-xs leading-relaxed text-muted break-keep">
            달러로 거래하는 미국 종목이라 매수 금액·평가금액·종가를 그날 원/달러 환율로 원화로 환산했어요. 수익률에는 환율
            변동도 들어 있어요.
          </p>
        )}
        {etf.currency === "KRW" && currency === "USD" && (
          <p className="text-xs leading-relaxed text-muted break-keep">
            원화로 거래하는 국내 종목이라 매수 금액·평가금액·종가를 그날 원/달러 환율로 나눠 달러로 나타냈어요.
          </p>
        )}
      </div>
      <LogSection
        etfName={etf.name}
        results={results}
        currency={currency}
        mode={mode}
        onModeChange={onModeChange}
        selectedDate={selectedDate}
        onSelectDate={setSelectedDate}
      />
      <StreakMetrics results={results} />
      <ValueChart
        etfName={etf.name}
        results={results}
        currency={currency}
        selectedDate={selectedDate} onSelectDate={setSelectedDate} />
      <SummaryCards results={results} currency={currency} />
      <PainMetrics results={results} currency={currency} />
    </div>
  );
}
