import type { CostsInsight, Instrument, Trade } from "@/lib/api/portfolio-types";
import { mockHoldingDetail } from "@/lib/api/mock/portfolio/holdings";
import { mockLog } from "@/lib/api/mock/portfolio/home";
import { INSTRUMENTS } from "@/lib/api/mock/portfolio/fixtures";

// 숨은 비용 mock.
// 체결은 종목 상세 mock(holdings.ts)의 것을 그대로 쓰고, 손익은 메인 로그 mock(home.ts)의 것을 쓴다.

const round4 = (v: number) => Math.round(v * 10_000) / 10_000;

/** 체결이 있는 종목의 체결 내역. 종목 상세 mock과 같은 숫자다. */
function tradesOf(inst: Instrument): Trade[] {
  try {
    return mockHoldingDetail(inst.code).trades;
  } catch {
    return [];
  }
}

export function mockCosts(year: number | null): CostsInsight {
  const latest = mockLog(null);
  const y = year ?? latest.year;
  const log = y === latest.year ? latest : mockLog(y);
  const trades = INSTRUMENTS.flatMap(tradesOf);
  const sum = (ts: Trade[]) => {
    const fee = ts.reduce((s, t) => s + t.fee, 0);
    const tax = ts.reduce((s, t) => s + t.tax, 0);
    return { fee, tax, cost: fee + tax };
  };

  const inYear = trades.filter((t) => t.date.startsWith(`${y}-`));
  const total = sum(inYear);
  const profitBeforeCost = log.stats.totalPnl + total.cost;

  // 그해 기록이 있는 달만
  const recordedMonths = [...new Set(log.days.filter((d) => d.source !== "MISSING").map((d) => d.date.slice(0, 7)))].sort();
  const months = recordedMonths.map((month) => {
    const { fee, tax } = sum(inYear.filter((t) => t.date.startsWith(month)));
    return { month, fee, tax };
  });

  const byInstrument = INSTRUMENTS.map((instrument) => {
    const ts = inYear.filter((t) => t.instrument.code === instrument.code);
    const { fee, tax } = sum(ts);
    return { instrument, fee, tax, trades: ts.length };
  })
    .filter((r) => r.trades > 0)
    .sort((a, b) => b.fee + b.tax - (a.fee + a.tax));

  return {
    year: y,
    availableYears: latest.availableYears,
    total,
    allTime: sum(trades),
    profitBeforeCost,
    costShare: profitBeforeCost > 0 ? round4(total.cost / profitBeforeCost) : null,
    months,
    byInstrument,
  };
}
