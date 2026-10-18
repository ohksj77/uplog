"use client";

import { useRouter } from "next/navigation";
import { useCallback, useMemo } from "react";
import { LogGridBase } from "@/components/log-grid/LogGridBase";
import { logColor } from "@/components/log-grid/logic";
import { LogScale, describeLevel } from "@/components/log-grid/LogScale";
import type { LogLevel, LogThresholds } from "@/components/log-grid/logic";
import { BACKFILLED_TEXT, accountLogCells, accountThresholds, backfilledBands } from "@/components/portfolio-home/log-cells";
import type { PortfolioLog } from "@/lib/api/portfolio-types";
import { formatDate, formatWonShort } from "@/lib/format";

/** 그해 계좌 로그. 칸을 누르면(Enter) 그날 상세로 간다. */
export function AccountLog({ log }: { log: PortfolioLog }) {
  const router = useRouter();
  const thresholds = useMemo(() => accountThresholds(log.days), [log.days]);
  const cells = useMemo(() => accountLogCells(log.days, thresholds), [log.days, thresholds]);
  const bands = useMemo(() => backfilledBands(log.days), [log.days]);
  const onActivate = useCallback((i: number) => router.push(`/days/${log.days[i].date}`), [router, log.days]);
  const missing = log.days.filter((d) => d.source === "MISSING").length;

  return (
    <div className="space-y-3 rounded-2xl bg-surface p-4">
      <LogGridBase
        cells={cells}
        label={`${log.year}년 일별 손익 로그. 칸을 고르고 Enter를 누르면 그날 상세로 이동합니다.`}
        onActivate={onActivate}
        bands={bands}
        emptyText="이 해에는 기록이 없어요"
      />
      <AccountLegend thresholds={thresholds} showMissing={missing > 0} backfilledUntil={bands[0]?.to ?? null} />
    </div>
  );
}

const LEVELS: LogLevel[] = [-4, -3, -2, -1, 1, 2, 3, 4];

function AccountLegend({ thresholds, showMissing, backfilledUntil }: { thresholds: LogThresholds; showMissing: boolean; backfilledUntil: string | null }) {
  return (
    <div className="space-y-2 text-[11px] text-muted">
      <div className="flex flex-wrap items-start gap-x-4 gap-y-2">
        <LogScale thresholds={thresholds} format={formatWonShort} unit="(원)" suffix="원" swatch={24} />
        <span className="flex items-center gap-1.5 leading-3">
          <span aria-hidden className="size-3 rounded-[2px]" style={{ backgroundColor: logColor(0) }} />
          변동 없음
        </span>
        {showMissing && (
          <span className="flex items-center gap-1.5 leading-3">
            <span aria-hidden className="size-3 rounded-[2px] opacity-60 shadow-[inset_0_0_0_1px_var(--muted)]" />
            기록 없음
          </span>
        )}
      </div>
      <p>진하기는 그해 하루 손익 크기의 사분위(칸 수가 단계마다 비슷하게)예요.</p>
      {backfilledUntil && (
        <p>
          <span aria-hidden className="mr-1 inline-block w-4 border-t border-dashed border-muted align-middle" />
          {formatDate(backfilledUntil)}까지는 계좌 연결 전이라 {BACKFILLED_TEXT}한 기록이에요.
        </p>
      )}
      <p className="sr-only">
        색 범례: {LEVELS.map((l) => `${describeLevel(l, thresholds, formatWonShort, "원")} ${Math.abs(l)}단계`).join(", ")}, 회색은 손익 0.
      </p>
    </div>
  );
}
