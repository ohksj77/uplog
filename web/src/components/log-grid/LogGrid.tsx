"use client";

import { useCallback, useMemo } from "react";

import { LogGridBase } from "@/components/log-grid/LogGridBase";
import { LOG_MODE_LABELS, LOG_ZERO_BAND, formatLogDate, formatLogValue, logCellLabel, logLevel, logValue } from "@/components/log-grid/logic";
import type { LogGridCell, LogGridProps, LogMode } from "@/components/log-grid/types";
import type { SimulationDay } from "@/lib/api/types";
import { formatMoney, toneOf, type MoneyCurrency } from "@/lib/format";

// 시뮬레이션 로그. 칸 값·색·문구만 정하고 그리기는 LogGridBase에 맡긴다.

export function LogGrid({ days, mode, label, onSelectDay, selectedDate = null, range, currency = "KRW" }: LogGridProps) {
  const cells = useMemo(() => simulationCells(days, mode, currency), [days, mode, currency]);
  const onActivate = useCallback((i: number) => onSelectDay?.(days[i] ?? null), [days, onSelectDay]);
  const onClear = useCallback(() => onSelectDay?.(null), [onSelectDay]);

  return (
    <LogGridBase
      cells={cells}
      label={`${label} — ${LOG_MODE_LABELS[mode]}`}
      selectedDate={selectedDate}
      range={range}
      onActivate={onActivate}
      onClear={onClear}
    />
  );
}

function simulationCells(days: readonly SimulationDay[], mode: LogMode, currency: MoneyCurrency): LogGridCell[] {
  const lastClose = days.length ? days[days.length - 1].close : 0;
  return days.map((day) => {
    const value = logValue(day, mode, lastClose);
    const tone = value === null ? "flat" : toneOf(Math.abs(value) < LOG_ZERO_BAND ? 0 : value);
    return {
      date: day.date,
      level: logLevel(value, mode),
      marker: mode !== "costBasis" && day.purchased ? "dot" : null,
      label: logCellLabel(day, value, mode, currency),
      tooltip: {
        title: formatLogDate(day.date),
        rows: [
          { label: LOG_MODE_LABELS[mode], value: formatLogValue(value, mode), tone },
          { label: "평가금액", value: formatMoney(day.value, currency) },
        ],
        note: day.purchased ? "● 매수일" : undefined,
      },
    };
  });
}
