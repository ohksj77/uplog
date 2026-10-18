// 계좌 로그 칸 만들기 — React 없는 순수 함수.
import { formatLogDate, levelFor, quartileThresholds, type LogThresholds } from "@/components/log-grid/logic";
import type { LogGridBand, LogGridCell, LogTooltipRow } from "@/components/log-grid/types";
import type { PortfolioLogDay } from "@/lib/api/portfolio-types";
import { formatPercent, formatSignedWon, formatWon, toneOf } from "@/lib/format";

export const BACKFILLED_TEXT = "체결 내역으로 복원";

/** 경계를 읽기 쉬운 값으로: 1만 원 이상은 만 원 단위, 그 아래는 천 원 단위. */
export function niceWon(v: number): number {
  if (v >= 10_000) return Math.round(v / 10_000) * 10_000;
  if (v >= 1_000) return Math.round(v / 1_000) * 1_000;
  return Math.round(v);
}

/**
 * 진하기 경계: 그해 기록 있는 날 중 손익이 0이 아닌 날의 |손익| 사분위수.
 * 이익·손실이 같은 경계를 써서 같은 진하기 = 같은 크기의 돈이다.
 */
export function accountThresholds(days: readonly PortfolioLogDay[]): LogThresholds {
  return quartileThresholds(
    days.filter((d) => d.source !== "MISSING").map((d) => d.pnl),
    niceWon,
  );
}

export function accountLogCells(days: readonly PortfolioLogDay[], thresholds: LogThresholds): LogGridCell[] {
  return days.map((d) => {
    const title = formatLogDate(d.date);
    if (d.source === "MISSING") {
      return {
        date: d.date,
        level: null,
        hollow: true,
        activatable: false,
        label: `${title}, 기록 없음`,
        tooltip: { title, rows: [], note: "기록 없음 — 이날 계좌 정보를 받지 못했어요" },
      };
    }
    const tone = toneOf(d.pnl);
    const rows: LogTooltipRow[] = [
      { label: "손익", value: formatSignedWon(d.pnl), tone },
      { label: "수익률", value: formatPercent(d.dailyReturn), tone },
      { label: "평가금액", value: formatWon(d.value) },
    ];
    if (d.netCashFlow !== 0) rows.push({ label: d.netCashFlow > 0 ? "입금" : "출금", value: formatWon(Math.abs(d.netCashFlow)) });
    const backfilled = d.source === "BACKFILLED";
    const parts = [title, `손익 ${formatSignedWon(d.pnl)}`, `수익률 ${formatPercent(d.dailyReturn)}`, `평가금액 ${formatWon(d.value)}`];
    if (backfilled) parts.push(BACKFILLED_TEXT);
    return {
      date: d.date,
      level: levelFor(d.pnl, thresholds),
      label: parts.join(", "),
      tooltip: { title, rows, note: backfilled ? `${BACKFILLED_TEXT}한 기록` : undefined },
    };
  });
}

/** BACKFILLED 구간(연속이라고 본다). 없으면 빈 배열. */
export function backfilledBands(days: readonly PortfolioLogDay[]): LogGridBand[] {
  const filled = days.filter((d) => d.source === "BACKFILLED");
  if (filled.length === 0) return [];
  return [{ from: filled[0].date, to: filled[filled.length - 1].date, label: BACKFILLED_TEXT }];
}
