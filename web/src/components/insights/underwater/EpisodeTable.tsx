"use client";

import { useState } from "react";
import type { UnderwaterInsight } from "@/lib/api/portfolio-types";
import { formatDate, formatPercent } from "@/lib/format";

const INITIAL = 10;

/** 하락 구간 표. 깊은 순(응답 순서 그대로). 구간은 수익률 기준이다. */
export function EpisodeTable({ episodes }: { episodes: UnderwaterInsight["episodes"] }) {
  const [all, setAll] = useState(false);
  const shown = all ? episodes : episodes.slice(0, INITIAL);

  return (
    <section aria-labelledby="uw-episodes" className="space-y-3">
      <div>
        <h2 id="uw-episodes" className="text-lg font-semibold">
          하락 구간
        </h2>
        <p className="mt-1 text-xs text-muted">
          고점 아래로 내려간 구간 {episodes.length}개를 깊은 순으로 보여줘요. 구간은 수익률 기준이고 일수는 달력일이에요.
        </p>
      </div>
      {episodes.length === 0 ? (
        <p className="rounded-2xl bg-surface p-5 text-sm text-muted">고점 아래로 내려간 적이 없어요.</p>
      ) : (
        <div className="overflow-x-auto rounded-2xl border border-border">
          <table className="w-full min-w-[34rem] text-sm tabular-nums">
            <thead className="bg-surface text-xs text-muted">
              <tr>
                <th scope="col" className="px-3 py-2 text-right font-medium">
                  깊이
                </th>
                <th scope="col" className="px-3 py-2 text-left font-medium">
                  고점
                </th>
                <th scope="col" className="px-3 py-2 text-left font-medium">
                  저점
                </th>
                <th scope="col" className="px-3 py-2 text-left font-medium">
                  회복
                </th>
                <th scope="col" className="px-3 py-2 text-right font-medium">
                  바닥까지
                </th>
                <th scope="col" className="px-3 py-2 text-right font-medium">
                  회복까지
                </th>
              </tr>
            </thead>
            <tbody>
              {shown.map((e) => {
                const ongoing = e.recovered == null;
                return (
                  <tr key={e.peak} className={`border-t border-border ${ongoing ? "bg-surface/60 font-medium" : ""}`}>
                    <td className="px-3 py-2 text-right text-loss">{formatPercent(e.depth, 1)}</td>
                    <td className="px-3 py-2">{formatDate(e.peak)}</td>
                    <td className="px-3 py-2">{formatDate(e.trough)}</td>
                    <td className="px-3 py-2">
                      {ongoing ? (
                        <span className="rounded-md border border-border px-1.5 py-0.5 text-xs">진행 중</span>
                      ) : (
                        formatDate(e.recovered!)
                      )}
                    </td>
                    <td className="px-3 py-2 text-right">{e.daysToTrough}일</td>
                    <td className="px-3 py-2 text-right">{e.daysToRecover == null ? "미회복" : `${e.daysToRecover}일`}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      {episodes.length > INITIAL && (
        <button
          type="button"
          onClick={() => setAll((v) => !v)}
          aria-expanded={all}
          className="h-11 w-full rounded-xl border border-border text-sm font-medium hover:bg-surface"
        >
          {all ? "접기" : `${episodes.length - INITIAL}개 더 보기`}
        </button>
      )}
    </section>
  );
}
