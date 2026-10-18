"use client";

import Link from "next/link";
import { useState } from "react";
import { formatLocal } from "@/components/holding/format";
import { Signed } from "@/components/result/Signed";
import type { HabitsInsight } from "@/lib/api/portfolio-types";
import { formatDate, formatPercent } from "@/lib/format";

// 물타기: 이미 들고 있는 종목을 평균 매수가보다 싸게 더 산 체결. 최신 순(응답 순서 그대로).

type Averaging = HabitsInsight["averagingDown"];

const INITIAL = 8;

export function averagingSummary({ buys, shareOfBuys, positions, positionsWon }: Averaging): { main: string; sub?: string } {
  if (buys === 0) return { main: "평균 매수가보다 싸게 더 산 적이 없어요." };
  const result =
    positionsWon === positions
      ? positions === 1
        ? "이익이에요"
        : "모두 이익이에요"
      : positionsWon === 0
        ? "이익인 포지션은 없어요"
        : `${positionsWon}개가 이익이에요`;
  return {
    main: `물타기한 포지션 ${positions}개 중 ${result}.`,
    sub: `전체 매수의 ${Math.round(shareOfBuys * 100)}%(${buys}번)가 평균 매수가보다 낮은 가격에 더 산 거였어요. 보유 중인 포지션은 오늘 평가 기준이에요.`,
  };
}

export function AveragingDown({ data }: { data: Averaging }) {
  const [all, setAll] = useState(false);
  const { main, sub } = averagingSummary(data);
  const shown = all ? data.items : data.items.slice(0, INITIAL);

  return (
    <section aria-labelledby="habits-averaging" className="space-y-3">
      <h2 id="habits-averaging" className="text-lg font-semibold">
        물타기
      </h2>
      <div>
        <p className="font-medium break-keep">{main}</p>
        {sub && <p className="mt-1 text-sm text-muted break-keep">{sub}</p>}
      </div>

      {data.items.length > 0 && (
        <ul className="divide-y divide-border rounded-2xl border border-border">
          {shown.map((it) => (
            <li key={`${it.date}-${it.instrument.code}`} className="flex flex-wrap items-start justify-between gap-x-4 gap-y-1 px-4 py-3">
              <div className="min-w-0">
                <p className="text-xs text-muted tabular-nums">{formatDate(it.date)}</p>
                <Link
                  href={`/holdings/${encodeURIComponent(it.instrument.code)}`}
                  className="font-medium break-keep hover:underline focus-visible:outline-2 focus-visible:outline-accent"
                >
                  {it.instrument.name}
                </Link>
                <p className="mt-0.5 text-sm tabular-nums">
                  {formatLocal(it.priceLocal, it.instrument.currency)}
                  <span className="text-muted">
                    {" "}
                    · 직전 평균 {formatLocal(it.avgCostLocal, it.instrument.currency)}보다 {(Math.abs(it.belowAvg) * 100).toFixed(1)}% 낮게
                  </span>
                </p>
              </div>
              <p className="text-sm tabular-nums">
                <span className="text-muted">포지션 </span>
                <Signed value={it.positionReturn} className="font-semibold">
                  {formatPercent(it.positionReturn, 1)}
                </Signed>
              </p>
            </li>
          ))}
        </ul>
      )}
      {data.items.length > INITIAL && (
        <button
          type="button"
          onClick={() => setAll((v) => !v)}
          aria-expanded={all}
          className="h-11 w-full rounded-xl border border-border text-sm font-medium hover:bg-surface"
        >
          {all ? "접기" : `${data.items.length - INITIAL}개 더 보기`}
        </button>
      )}
    </section>
  );
}
