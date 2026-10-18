import { ApiError } from "@/lib/api/client";
import type { IsoDate, Streak, StreakStats } from "@/lib/api/types";
import type {
  CashFlowItem,
  Holding,
  Instrument,
  PortfolioDay,
  PortfolioLog,
  PortfolioLogDay,
  PortfolioSummary,
  Trade,
} from "@/lib/api/portfolio-types";
import { mockOpenPositions } from "@/lib/api/mock/portfolio/holdings";
import { MOCK_FIRST_DATE, MOCK_LAST_DATE, accountSeries, closeOn, instrument, rng } from "@/lib/api/mock/portfolio/fixtures";

// 메인(수익 로그)·하루 상세 mock. 담당: home 영역. fixtures.ts 재료로 만든다.
// 평가금 시계열은 accountSeries 그대로, 종목 구성은 아래 PLANS로 그럴듯하게 꾸민다.

/** 이날 전은 계좌 연결 전이라 체결 내역으로 복원(BACKFILLED). */
const COLLECTED_FROM = "2026-07-15";
/** 수집 실패한 날. 월초(입금일)가 아니어야 한다. */
const MISSING_DATES = new Set(["2026-10-06", "2026-10-13"]);
/** 평가금 중 종목에 들어간 비율(나머지는 현금). */
const INVESTED = 0.9;

type Plan = { code: string; weight: number; gain: number; since: IsoDate; until?: IsoDate };

/** 보유 계획. weight는 같은 날 보유 종목끼리 정규화한다. gain은 현재 보유분의 평균 단가 대비 수익률. */
const PLANS: Plan[] = [
  { code: "S00001", weight: 0.24, gain: 0.18, since: MOCK_FIRST_DATE },
  { code: "S00003", weight: 0.16, gain: 0.09, since: MOCK_FIRST_DATE },
  { code: "SMPT", weight: 0.19, gain: 0.32, since: "2024-11-04" },
  { code: "S00004", weight: 0.08, gain: 0, since: "2025-01-06", until: "2025-09-01" },
  { code: "SMPS", weight: 0.14, gain: 0.21, since: "2025-02-03" },
  { code: "SMPE", weight: 0.06, gain: 0, since: "2025-04-01", until: "2026-02-02" },
  { code: "S00002", weight: 0.09, gain: -0.14, since: "2025-06-02" },
  { code: "SMPC", weight: 0.08, gain: -0.06, since: "2026-03-03" },
];

const inst = (code: string): Instrument => instrument(code)!;
const fxOn = (code: string, date: IsoDate) => (inst(code).currency === "USD" ? closeOn("USDKRW", date) : 1);
const krwPrice = (code: string, date: IsoDate) => closeOn(code, date) * fxOn(code, date);
const round = (v: number, digits: number) => Math.round(v * 10 ** digits) / 10 ** digits;

/** 그날 장 마감 후 보유 중인 종목과 비중(합 = INVESTED). */
function weightsOn(date: IsoDate): { plan: Plan; w: number }[] {
  const active = PLANS.filter((p) => p.since <= date && (!p.until || date < p.until));
  const sum = active.reduce((s, p) => s + p.weight, 0);
  return active.map((plan) => ({ plan, w: (plan.weight / sum) * INVESTED }));
}

// ── 일별 기록 ───────────────────────────────────────────────────────────

type Row = PortfolioLogDay & { /** 직전 기록일 평가금 */ prevValue: number };

let cachedRows: Row[] | null = null;

/** pnl = value − netCashFlow − 직전 기록일 value. MISSING 다음 날은 빈 날까지 몰아서 계산한다. */
function rows(): Row[] {
  if (cachedRows) return cachedRows;
  let prev = 0;
  cachedRows = accountSeries().map((d): Row => {
    if (MISSING_DATES.has(d.date)) {
      return { date: d.date, value: 0, netCashFlow: 0, pnl: 0, dailyReturn: 0, source: "MISSING", prevValue: prev };
    }
    const pnl = d.value - d.netCashFlow - prev;
    const row: Row = {
      date: d.date,
      value: d.value,
      netCashFlow: d.netCashFlow,
      pnl,
      dailyReturn: prev > 0 ? round(pnl / prev, 6) : 0,
      source: d.date < COLLECTED_FROM ? "BACKFILLED" : "COLLECTED",
      prevValue: prev,
    };
    prev = d.value;
    return row;
  });
  return cachedRows;
}

const recorded = (r: PortfolioLogDay) => r.source !== "MISSING";
function strip(row: Row): PortfolioLogDay {
  const { date, value, netCashFlow, pnl, dailyReturn, source } = row;
  return { date, value, netCashFlow, pnl, dailyReturn, source };
}

/** 백엔드 calc-engine Streaks와 같은 규칙(mock.ts의 runs와 같음). 기록 없는 날은 연속을 끊는다. */
function runs(days: PortfolioLogDay[], hit: (d: PortfolioLogDay) => boolean): StreakStats {
  let longest: Streak | null = null;
  let running: Streak | null = null;
  for (const d of days) {
    if (recorded(d) && hit(d)) {
      running = running ? { length: running.length + 1, start: running.start, end: d.date } : { length: 1, start: d.date, end: d.date };
      if (!longest || running.length > longest.length) longest = running;
    } else {
      running = null;
    }
  }
  return { longest, current: running };
}

// ── 응답 ────────────────────────────────────────────────────────────────

export function mockSummary(): PortfolioSummary {
  const all = rows().filter(recorded);
  const last = all[all.length - 1];
  const netDeposits = all.reduce((s, r) => s + r.netCashFlow, 0);
  const twr = all.reduce((g, r) => g * (1 + r.dailyReturn), 1) - 1;
  return {
    account: { name: "데모 계좌", broker: "TOSS", sample: true, firstDate: MOCK_FIRST_DATE, lastDate: MOCK_LAST_DATE },
    asOf: last.date,
    totalValue: last.value,
    netDeposits,
    profit: last.value - netDeposits,
    twr: round(twr, 4),
    todayPnl: last.pnl,
    todayReturn: round(last.dailyReturn, 4),
  };
}

export function mockLog(year: number | null): PortfolioLog {
  const all = rows();
  const availableYears = [...new Set(all.map((r) => Number(r.date.slice(0, 4))))];
  const y = year ?? Number(MOCK_LAST_DATE.slice(0, 4));
  const days = all.filter((r) => r.date.startsWith(`${y}-`)).map(strip);
  const rec = days.filter(recorded);
  const best = rec.reduce<PortfolioLogDay | null>((m, d) => (!m || d.pnl > m.pnl ? d : m), null);
  const worst = rec.reduce<PortfolioLogDay | null>((m, d) => (!m || d.pnl < m.pnl ? d : m), null);
  return {
    year: y,
    availableYears,
    from: days[0]?.date ?? `${y}-01-01`,
    to: days[days.length - 1]?.date ?? `${y}-12-31`,
    days,
    stats: {
      gainDays: rec.filter((d) => d.pnl > 0).length,
      lossDays: rec.filter((d) => d.pnl < 0).length,
      flatDays: rec.filter((d) => d.pnl === 0).length,
      totalPnl: rec.reduce((s, d) => s + d.pnl, 0),
      bestDay: best && best.pnl > 0 ? { date: best.date, pnl: best.pnl } : null,
      worstDay: worst && worst.pnl < 0 ? { date: worst.date, pnl: worst.pnl } : null,
    },
    streaks: { gain: runs(days, (d) => d.pnl > 0), loss: runs(days, (d) => d.pnl < 0) },
  };
}

/**
 * 종목 상세(holdings.ts)와 같은 포지션을 쓴다. 비중 = 평가금액 / 계좌 총 평가금액.
 * 보유 종목 평가금 합이 계좌 평가금보다 크면(두 mock의 시세 재료가 달라서) 보유 합을 분모로 쓴다.
 */
export function mockHoldings(): Holding[] {
  const positions = mockOpenPositions();
  const held = positions.reduce((s, p) => s + p.value, 0);
  const total = Math.max(mockSummary().totalValue, held);
  return positions
    .map((p): Holding => ({
      instrument: p.instrument,
      quantity: p.quantity,
      avgCostLocal: p.avgCostLocal,
      priceLocal: p.priceLocal,
      fxRate: p.fxRate,
      value: p.value,
      profit: p.profit,
      returnRate: p.returnRate,
      weight: total > 0 ? round(p.value / total, 4) : 0,
    }))
    .sort((a, b) => b.value - a.value);
}

export function mockDay(date: string): PortfolioDay {
  const all = rows();
  const i = all.findIndex((r) => r.date === date);
  const row = all[i];
  if (!row || !recorded(row)) {
    throw new ApiError(404, "DATE_NOT_FOUND", "거래일이 아니거나 기록이 없는 날이에요.");
  }
  const prev = all.slice(0, i).reverse().find(recorded);
  const next = all.slice(i + 1).find(recorded);

  const trades = tradesOn(row);
  const dividends = dividendsOn(row);
  const costs = trades.reduce((s, t) => s + t.fee + t.tax, 0);
  const cashFlows: CashFlowItem[] = [
    ...(row.netCashFlow > 0 ? [{ type: "DEPOSIT" as const, amount: row.netCashFlow, instrument: null }] : []),
    ...(row.netCashFlow < 0 ? [{ type: "WITHDRAWAL" as const, amount: -row.netCashFlow, instrument: null }] : []),
    ...dividends,
  ];
  const dividendTotal = dividends.reduce((s, d) => s + d.amount, 0);

  return {
    date,
    prevDate: prev?.date ?? null,
    nextDate: next?.date ?? null,
    value: row.value,
    netCashFlow: row.netCashFlow,
    pnl: row.pnl,
    dailyReturn: row.dailyReturn,
    contributions: contributionsOn(row, i > 0 ? all[i - 1].date : null, row.pnl - dividendTotal + costs),
    trades,
    cashFlows,
  };
}

/**
 * 종목별 손익. 전날 비중 × 전날 평가금 × 그날 원화 수익률로 나눈 뒤,
 * 합이 target(= pnl − 배당 + 수수료 + 세금)과 맞도록 차이를 비중대로 나눠 얹는다.
 */
function contributionsOn(row: Row, prevDate: IsoDate | null, target: number): PortfolioDay["contributions"] {
  if (!prevDate || row.prevValue <= 0) return [];
  const held = weightsOn(prevDate);
  if (held.length === 0) return [];
  const raw = held.map(({ plan, w }) => row.prevValue * w * (krwPrice(plan.code, row.date) / krwPrice(plan.code, prevDate) - 1));
  const sumW = held.reduce((s, h) => s + h.w, 0);
  const residual = target - raw.reduce((s, v) => s + v, 0);
  const pnls = raw.map((v, k) => Math.round(v + (residual * held[k].w) / sumW));
  // 반올림 차이는 가장 큰 칸에 몰아 합을 정확히 맞춘다.
  const drift = target - pnls.reduce((s, v) => s + v, 0);
  const big = pnls.reduce((m, v, k) => (Math.abs(v) > Math.abs(pnls[m]) ? k : m), 0);
  pnls[big] += drift;
  return held
    .map(({ plan, w }, k) => ({
      instrument: inst(plan.code),
      pnl: pnls[k],
      dailyReturn: round(pnls[k] / (row.prevValue * w), 4),
      weight: round(w, 4),
    }))
    .sort((a, b) => Math.abs(b.pnl) - Math.abs(a.pnl));
}

function trade(code: string, date: IsoDate, side: Trade["side"], targetAmount: number): Trade {
  const priceLocal = closeOn(code, date);
  const fxRate = fxOn(code, date);
  const quantity = Math.max(1, Math.round(targetAmount / (priceLocal * fxRate)));
  const amount = Math.round(quantity * priceLocal * fxRate);
  const kr = fxRate === 1;
  return {
    date,
    instrument: inst(code),
    side,
    quantity,
    priceLocal,
    fxRate,
    amount,
    fee: Math.round(amount * (kr ? 0.00015 : 0.001)),
    tax: side === "SELL" && kr ? Math.round(amount * 0.0018) : 0,
  };
}

/** 첫 편입일 매수, 편출일 매도, 입금일 매수, 출금일 매도, 가끔 리밸런싱. */
function tradesOn(row: Row): Trade[] {
  const rand = rng(`trade:${row.date}`);
  const base = row.prevValue || row.value;
  const out: Trade[] = [];
  const held = weightsOn(row.date);
  for (const p of PLANS) {
    if (p.since === row.date) out.push(trade(p.code, row.date, "BUY", base * (held.find((h) => h.plan === p)?.w ?? 0.1)));
    if (p.until === row.date) out.push(trade(p.code, row.date, "SELL", base * 0.08));
  }
  const pick = () => held[Math.floor(rand() * held.length)]?.plan.code;
  if (row.prevValue > 0 && row.netCashFlow > 0) {
    const code = pick();
    if (code) out.push(trade(code, row.date, "BUY", row.netCashFlow * 0.9));
  } else if (row.netCashFlow < 0) {
    const code = pick();
    if (code) out.push(trade(code, row.date, "SELL", -row.netCashFlow));
  } else if (rand() < 0.06) {
    const code = pick();
    if (code) out.push(trade(code, row.date, rand() < 0.5 ? "BUY" : "SELL", 300_000 + rand() * 1_200_000));
  }
  return out;
}

function dividendsOn(row: Row): CashFlowItem[] {
  return weightsOn(row.date)
    .filter(({ plan }) => rng(`div:${plan.code}:${row.date}`)() < 0.012)
    .map(({ plan, w }) => ({ type: "DIVIDEND" as const, amount: Math.max(1000, Math.round((row.value * w * 0.004) / 10) * 10), instrument: inst(plan.code) }));
}
