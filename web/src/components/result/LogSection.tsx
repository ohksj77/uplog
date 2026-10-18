"use client";

import { LogGrid, LogLegend, LogModeToggle } from "@/components/log-grid";
import type { LogMode } from "@/components/log-grid/types";
import { DayDetail } from "@/components/result/DayDetail";
import { MODE_HINT } from "@/components/result/mode-hint";
import { STRATEGIES, STRATEGY_LABEL, type Strategy } from "@/components/result/text";
import type { SimulationDay, SimulationResult } from "@/lib/api/types";

type LogSectionProps = {
  etfName: string;
  results: Record<Strategy, SimulationResult>;
  mode: LogMode;
  onModeChange: (mode: LogMode) => void;
  selectedDate: string | null;
  onSelectDate: (date: string | null) => void;
};

/** 거치식·적립식 로그를 위아래로. 보기 전환과 고른 날은 두 줄이 함께 쓴다. */
export function LogSection({ etfName, results, mode, onModeChange, selectedDate, onSelectDate }: LogSectionProps) {
  const pick = (day: SimulationDay | null) => onSelectDate(day?.date ?? null);
  const dayOf = (s: Strategy) => results[s].days.find((d) => d.date === selectedDate);
  // 적립식은 첫 매수일부터 시작하므로, 두 줄이 같은 열에 같은 날짜가 오도록 구간을 맞춘다.
  const all = STRATEGIES.flatMap((s) => results[s].days);
  const range = all.length
    ? {
        from: all.reduce((m, d) => (d.date < m ? d.date : m), all[0].date),
        to: all.reduce((m, d) => (d.date > m ? d.date : m), all[0].date),
      }
    : undefined;

  return (
    <section aria-labelledby="log-heading" className="space-y-4">
      <h2 id="log-heading" className="text-lg font-semibold">
        하루하루 로그
      </h2>
      <div>
        <LogModeToggle value={mode} onChange={onModeChange} />
        <p className="mt-2 text-xs text-muted">{MODE_HINT[mode]}</p>
      </div>
      {STRATEGIES.map((s) => (
        <div key={s} className="rounded-2xl bg-surface p-4">
          <h3 className="mb-3 text-sm font-semibold">{STRATEGY_LABEL[s]}</h3>
          <div className="min-w-0">
            <LogGrid
              days={results[s].days}
              mode={mode}
              label={`${etfName} ${STRATEGY_LABEL[s]} 로그`}
              onSelectDay={pick}
              selectedDate={selectedDate}
              range={range}
            />
          </div>
        </div>
      ))}
      <LogLegend mode={mode} />
      {selectedDate && (
        <DayDetail
          date={selectedDate}
          days={{ lumpSum: dayOf("lumpSum"), recurring: dayOf("recurring") }}
          onClose={() => onSelectDate(null)}
        />
      )}
    </section>
  );
}
