import type { IsoDate } from "@/lib/api/types";
import type { CostsInsight, DividendsInsight, Instrument, Trade } from "@/lib/api/portfolio-types";
import { mockHoldingDetail } from "@/lib/api/mock/portfolio/holdings";
import { mockLog, mockSummary } from "@/lib/api/mock/portfolio/home";
import { INSTRUMENTS, MOCK_FIRST_DATE, MOCK_LAST_DATE, closeOn, weekdays } from "@/lib/api/mock/portfolio/fixtures";

// 배당 흐름·숨은 비용 mock. 담당: income 영역.
// 체결은 종목 상세 mock(holdings.ts)의 것을 그대로 쓰고, 손익은 메인 로그 mock(home.ts)의 것을 쓴다.

/** holdings.ts의 배당 규칙과 같다: 분기(3·6·9·12월) 10일 이후 첫 거래일, 그날 체결 전 보유 수량 × 종가 × 비율. */
const DIVIDEND_YIELD: Record<string, number> = { S00001: 0.004, S00003: 0.003, SMPS: 0.0035 };

type DividendEvent = { date: IsoDate; instrument: Instrument; amount: number };

const round4 = (v: number) => Math.round(v * 10_000) / 10_000;

/** 체결이 있는 종목의 체결 내역. 종목 상세 mock과 같은 숫자다. */
function tradesOf(inst: Instrument): Trade[] {
  try {
    return mockHoldingDetail(inst.code).trades;
  } catch {
    return [];
  }
}

let events: DividendEvent[] | null = null;

function dividendEvents(): DividendEvent[] {
  if (events) return events;
  const days = weekdays();
  const out: DividendEvent[] = [];
  for (const inst of INSTRUMENTS) {
    const rate = DIVIDEND_YIELD[inst.code];
    if (!rate) continue;
    const trades = tradesOf(inst);
    let qty = 0;
    let next = 0;
    for (let i = 0; i < days.length; i++) {
      const date = days[i];
      const quarter = [3, 6, 9, 12].includes(Number(date.slice(5, 7)));
      if (qty > 0 && quarter && date.slice(8) >= "10" && days[i - 1]?.slice(8) < "10") {
        const fx = inst.currency === "USD" ? closeOn("USDKRW", date) : 1;
        out.push({ date, instrument: inst, amount: Math.round(qty * closeOn(inst.code, date) * fx * rate) });
      }
      while (next < trades.length && trades[next].date === date) {
        const t = trades[next++];
        qty += t.side === "BUY" ? t.quantity : -t.quantity;
      }
    }
  }
  events = out.sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : a.instrument.code.localeCompare(b.instrument.code)));
  return events;
}

/** from~to(YYYY-MM) 사이 모든 달, 오래된 순. */
function monthsBetween(from: string, to: string): string[] {
  const out: string[] = [];
  let y = Number(from.slice(0, 4));
  let m = Number(from.slice(5, 7));
  for (let key = from; key <= to; ) {
    out.push(key);
    m += 1;
    if (m > 12) {
      m = 1;
      y += 1;
    }
    key = `${y}-${String(m).padStart(2, "0")}`;
  }
  return out;
}

/** "2026-10-16" → "2025-10-16" */
const yearBefore = (date: IsoDate) => `${Number(date.slice(0, 4)) - 1}${date.slice(4)}`;

export function mockDividends(): DividendsInsight {
  const all = dividendEvents();
  const months = monthsBetween(MOCK_FIRST_DATE.slice(0, 7), MOCK_LAST_DATE.slice(0, 7)).map((month) => {
    const items = all.filter((e) => e.date.startsWith(month));
    return { month, amount: items.reduce((s, e) => s + e.amount, 0), items };
  });

  const years = [...new Set(months.map((m) => Number(m.month.slice(0, 4))))];
  const byYear = years.map((year) => ({
    year,
    amount: months.filter((m) => m.month.startsWith(`${year}-`)).reduce((s, m) => s + m.amount, 0),
  }));

  const byCode = new Map<string, { instrument: Instrument; amount: number; count: number }>();
  for (const e of all) {
    const row = byCode.get(e.instrument.code) ?? { instrument: e.instrument, amount: 0, count: 0 };
    row.amount += e.amount;
    row.count += 1;
    byCode.set(e.instrument.code, row);
  }

  const since = yearBefore(MOCK_LAST_DATE);
  const trailing12m = all.filter((e) => e.date > since && e.date <= MOCK_LAST_DATE).reduce((s, e) => s + e.amount, 0);
  const totalValue = mockSummary().totalValue;

  return {
    months,
    byYear,
    byInstrument: [...byCode.values()].sort((a, b) => b.amount - a.amount),
    total: all.reduce((s, e) => s + e.amount, 0),
    trailing12m,
    trailingYield: totalValue > 0 ? round4(trailing12m / totalValue) : 0,
  };
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
