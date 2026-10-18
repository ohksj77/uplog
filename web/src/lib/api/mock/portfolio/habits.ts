import type { HabitsInsight, HabitsVerdict, HoldingPeriod, HoldingSideStats, MonthlyInsight } from "@/lib/api/portfolio-types";
import { INSTRUMENTS, MOCK_FIRST_DATE, MOCK_LAST_DATE, accountSeries } from "@/lib/api/mock/portfolio/fixtures";
import { mockHoldingDetail, mockHoldingPeriods } from "@/lib/api/mock/portfolio/holdings";
import { mockLog } from "@/lib/api/mock/portfolio/home";

// 월별 성적표·매매 습관 mock. 담당: habits 영역.
// 월별은 수익 로그(home.ts)의 일별 기록으로, 습관은 종목 상세(holdings.ts)의 체결·포지션으로 계산한다.

const round4 = (v: number) => Math.round(v * 10_000) / 10_000;
const round2 = (v: number) => Math.round(v * 100) / 100;

// ── 월별 성적표 ─────────────────────────────────────────────────────────

export function mockMonthly(): MonthlyInsight {
  const { availableYears } = mockLog(null);
  const cells: { year: number; month: number; return: number }[] = [];

  const years = [...availableYears].sort((a, b) => a - b).map((year) => {
    const log = mockLog(year);
    const recorded = log.days.filter((d) => d.source !== "MISSING");
    const months = Array.from({ length: 12 }, (_, i) => {
      const month = i + 1;
      const days = recorded.filter((d) => Number(d.date.slice(5, 7)) === month);
      if (days.length === 0) return { month, return: null, pnl: null, netCashFlow: null };
      const r = round4(days.reduce((g, d) => g * (1 + d.dailyReturn), 1) - 1);
      cells.push({ year, month, return: r });
      return {
        month,
        return: r,
        pnl: days.reduce((s, d) => s + d.pnl, 0),
        netCashFlow: days.reduce((s, d) => s + d.netCashFlow, 0),
      };
    });
    return {
      year,
      months,
      total: { return: round4(recorded.reduce((g, d) => g * (1 + d.dailyReturn), 1) - 1), pnl: log.stats.totalPnl },
    };
  });

  const best = cells.reduce<(typeof cells)[number] | null>((m, c) => (!m || c.return > m.return ? c : m), null);
  const worst = cells.reduce<(typeof cells)[number] | null>((m, c) => (!m || c.return < m.return ? c : m), null);
  return {
    years,
    best,
    worst,
    positiveMonths: cells.filter((c) => c.return > 0).length,
    negativeMonths: cells.filter((c) => c.return < 0).length,
  };
}

// ── 매매 습관 ───────────────────────────────────────────────────────────

function sideStats(ps: HoldingPeriod[]): HoldingSideStats {
  const n = ps.length;
  if (n === 0) return { count: 0, avgDays: 0, medianDays: 0, avgReturn: 0 };
  const days = ps.map((p) => p.days).sort((a, b) => a - b);
  const median = n % 2 ? days[(n - 1) / 2] : (days[n / 2 - 1] + days[n / 2]) / 2;
  return {
    count: n,
    avgDays: Math.round(days.reduce((a, d) => a + d, 0) / n),
    medianDays: Math.round(median),
    avgReturn: round4(ps.reduce((a, p) => a + p.returnRate, 0) / n),
  };
}

function holdingOf(periods: HoldingPeriod[]): HabitsInsight["holding"] {
  const closed = periods.filter((p) => p.status === "CLOSED");
  const winList = closed.filter((p) => p.returnRate > 0);
  const loseList = closed.filter((p) => p.returnRate <= 0);
  const avg = (ps: HoldingPeriod[]) => ps.reduce((a, p) => a + p.days, 0) / ps.length;
  const ratio = winList.length && loseList.length ? avg(loseList) / avg(winList) : null;
  let verdict: HabitsVerdict;
  if (winList.length < 2 || loseList.length < 2 || ratio == null) verdict = "INSUFFICIENT";
  else if (ratio >= 1.5) verdict = "HOLD_LOSERS";
  else if (ratio <= 1 / 1.5) verdict = "CUT_LOSSES";
  else verdict = "BALANCED";
  return { winners: sideStats(winList), losers: sideStats(loseList), ratio: ratio == null ? null : round2(ratio), verdict };
}

function averagingDownOf(): HabitsInsight["averagingDown"] {
  const items: HabitsInsight["averagingDown"]["items"] = [];
  let totalBuys = 0;
  for (const inst of INSTRUMENTS) {
    let detail;
    try {
      detail = mockHoldingDetail(inst.code);
    } catch {
      continue; // 체결 없는 종목
    }
    const usd = inst.currency === "USD";
    let qty = 0;
    let costLocal = 0;
    for (const t of detail.trades) {
      if (t.side === "BUY") {
        totalBuys++;
        const avg = qty > 0 ? costLocal / qty : 0;
        if (qty > 0 && t.priceLocal < avg) {
          const period = detail.periods.find((p) => p.open <= t.date && (p.close == null || t.date <= p.close));
          items.push({
            date: t.date,
            instrument: inst,
            priceLocal: t.priceLocal,
            avgCostLocal: usd ? round2(avg) : Math.round(avg),
            belowAvg: round4(t.priceLocal / avg - 1),
            positionId: period?.id ?? "p-0",
            positionReturn: period?.returnRate ?? 0,
          });
        }
        qty += t.quantity;
        costLocal += t.quantity * t.priceLocal;
      } else {
        const share = t.quantity / qty;
        costLocal -= costLocal * share;
        qty -= t.quantity;
        if (qty === 0) costLocal = 0;
      }
    }
  }
  items.sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : a.instrument.code.localeCompare(b.instrument.code)));
  const positions = new Map<string, number>(items.map((i) => [i.positionId, i.positionReturn]));
  return {
    buys: items.length,
    shareOfBuys: totalBuys ? round4(items.length / totalBuys) : 0,
    positions: positions.size,
    positionsWon: [...positions.values()].filter((r) => r > 0).length,
    items,
  };
}

function activityOf(): HabitsInsight["activity"] {
  const trades = INSTRUMENTS.flatMap((inst) => {
    try {
      return mockHoldingDetail(inst.code).trades;
    } catch {
      return [];
    }
  });
  const account = accountSeries();
  const out: HabitsInsight["activity"] = [];
  const last = MOCK_LAST_DATE.slice(0, 7);
  for (let [y, m] = MOCK_FIRST_DATE.slice(0, 7).split("-").map(Number); ; ) {
    const month = `${y}-${String(m).padStart(2, "0")}`;
    if (month > last) break;
    const ts = trades.filter((t) => t.date.startsWith(month));
    const values = account.filter((d) => d.date.startsWith(month)).map((d) => d.value);
    const avgValue = values.length ? values.reduce((a, v) => a + v, 0) / values.length : 0;
    const sold = ts.filter((t) => t.side === "SELL").reduce((a, t) => a + t.amount, 0);
    out.push({
      month,
      buys: ts.filter((t) => t.side === "BUY").length,
      sells: ts.filter((t) => t.side === "SELL").length,
      turnover: avgValue > 0 ? round4(sold / avgValue) : 0,
    });
    m++;
    if (m > 12) {
      m = 1;
      y++;
    }
  }
  return out;
}

export function mockHabits(): HabitsInsight {
  return {
    holding: holdingOf(mockHoldingPeriods().periods),
    averagingDown: averagingDownOf(),
    activity: activityOf(),
  };
}
