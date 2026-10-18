import type {
  EtfListItem,
  IsoDate,
  SimulationDay,
  SimulationParams,
  SimulationResponse,
  SimulationResult,
  Streak,
  StreakStats,
  StreakUnit,
} from "@/lib/api/types";

// 백엔드 없이 화면을 개발하기 위한 데이터(NEXT_PUBLIC_USE_MOCK=1). 종목 목록은 실제 종목 코드·이름을 흉내 내지만
// 시세는 종목코드를 시드로 만든 랜덤워크다. 화면에는 실제 응답과 똑같이 보인다(샘플 표시 없음).

const MOCK_LAST_DATE = "2026-10-16";

const INSTRUMENTS: EtfListItem[] = [
  { code: "069500", name: "KODEX 200", market: "KR", type: "ETF", currency: "KRW", firstDate: "2020-01-02", lastDate: MOCK_LAST_DATE },
  { code: "360750", name: "TIGER 미국S&P500", market: "KR", type: "ETF", currency: "KRW", firstDate: "2020-08-07", lastDate: MOCK_LAST_DATE },
  { code: "SPY", name: "SPDR S&P 500 ETF", market: "US", type: "ETF", currency: "USD", firstDate: "2020-01-02", lastDate: MOCK_LAST_DATE },
  { code: "QQQ", name: "인베스코 QQQ", market: "US", type: "ETF", currency: "USD", firstDate: null, lastDate: null },
  { code: "005930", name: "삼성전자", market: "KR", type: "STOCK", currency: "KRW", firstDate: "2020-01-02", lastDate: MOCK_LAST_DATE },
  { code: "AAPL", name: "애플", market: "US", type: "STOCK", currency: "USD", firstDate: null, lastDate: null },
];

/** 백엔드와 같은 순서: 코드 일치 → ETF → 주식, 그 안에서 이름순. */
export function mockInstruments(query: string): EtfListItem[] {
  const q = query.trim().toLowerCase();
  const rank = (e: EtfListItem) => (e.code.toLowerCase() === q ? 0 : 2) + (e.type === "ETF" ? 0 : 1);
  return INSTRUMENTS.filter((e) => !q || e.name.toLowerCase().includes(q) || e.code.toLowerCase().includes(q)).sort(
    (a, b) => rank(a) - rank(b) || a.name.localeCompare(b.name, "ko"),
  );
}

const iso = (d: Date): IsoDate => d.toISOString().slice(0, 10);

function weekdays(from: Date, to: Date): Date[] {
  const days: Date[] = [];
  for (const d = new Date(from); d <= to; d.setUTCDate(d.getUTCDate() + 1)) {
    const dow = d.getUTCDay();
    if (dow !== 0 && dow !== 6) days.push(new Date(d));
  }
  return days;
}

/** 종목코드로 시드를 정하는 결정적 랜덤워크. */
function prices(code: string, days: Date[]): number[] {
  let seed = [...code].reduce((a, c) => a * 31 + c.charCodeAt(0), 7) >>> 0;
  const rand = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 2 ** 32);
  const drift = 0.0004 + (seed % 5) * 0.0001;
  const vol = 0.009 + (seed % 7) * 0.002;
  let p = 10_000 + (seed % 40) * 1_000;
  return days.map(() => {
    p *= 1 + drift + vol * (rand() + rand() + rand() - 1.5) * 1.4;
    return Math.round(p);
  });
}

function purchaseIndexes(days: Date[], p: SimulationParams): number[] {
  const idx = new Set<number>();
  const dowNum = { MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5 } as const;
  if (p.frequency === "DAILY") days.forEach((_, i) => idx.add(i));
  if (p.frequency === "WEEKLY") {
    const want = dowNum[p.dayOfWeek ?? "MONDAY"];
    // 해당 요일이 속한 주의 첫 거래일 이후 가장 가까운 날(휴장 없는 mock이라 그 요일 그대로)
    days.forEach((d, i) => d.getUTCDay() === want && idx.add(i));
  }
  if (p.frequency === "MONTHLY") {
    const want = p.dayOfMonth ?? 1;
    const seen = new Set<string>();
    days.forEach((d, i) => {
      const ym = iso(d).slice(0, 7);
      const last = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0)).getUTCDate();
      if (!seen.has(ym) && d.getUTCDate() >= Math.min(want, last)) {
        seen.add(ym);
        idx.add(i);
      }
    });
  }
  return [...idx].sort((a, b) => a - b);
}

/** 백엔드 calc-engine Streaks와 같은 규칙. */
function runs<T>(items: T[], hit: (t: T) => boolean, start: (t: T) => IsoDate, end: (t: T) => IsoDate): StreakStats {
  let longest: Streak | null = null;
  let running: Streak | null = null;
  for (const item of items) {
    if (hit(item)) {
      running = running
        ? { length: running.length + 1, start: running.start, end: end(item) }
        : { length: 1, start: start(item), end: end(item) };
      if (!longest || running.length > longest.length) longest = running;
    } else {
      running = null;
    }
  }
  return { longest, current: running };
}

function periodOf(date: IsoDate, unit: StreakUnit): string {
  if (unit === "DAY") return date;
  if (unit === "MONTH") return date.slice(0, 7);
  const d = new Date(`${date}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() - ((d.getUTCDay() + 6) % 7));
  return iso(d);
}

function purchaseStreaks(days: SimulationDay[], unit: StreakUnit): StreakStats {
  const periods = new Map<string, IsoDate[]>();
  for (const d of days) {
    const key = periodOf(d.date, unit);
    if (!periods.has(key)) periods.set(key, []);
    if (d.purchased) periods.get(key)!.push(d.date);
  }
  const list = [...periods.values()];
  const go = (xs: IsoDate[][]) => runs(xs, (b) => b.length > 0, (b) => b[0], (b) => b[b.length - 1]);
  const stats = go(list);
  // 마지막 주기에 아직 매수가 없으면 직전 주기까지를 현재 연속으로 본다.
  if (!stats.current && list.length >= 2) stats.current = go(list.slice(0, -1)).current;
  return stats;
}

/** 원/달러 mock 환율: 1,500원 근처에서 시작해 천천히 내려가는 결정적 흐름(원화 강세). */
function mockFx(days: Date[]): number[] {
  return days.map((_, i) => 1_500 - (150 * i) / Math.max(1, days.length - 1) + 12 * Math.sin(i / 9));
}

const roundWon = (n: number) => Math.round(n);
const roundCents = (n: number) => Math.round(n * 100) / 100;

/**
 * closes·buys는 기준 통화. 달러 기준은 원화 종가와 매수 금액을 그날 환율로 나눠 넘기면 된다(수량은 같고 금액만 달러).
 * round는 응답 금액 반올림(원 단위 / 센트 단위).
 */
function replay(
  days: Date[],
  closes: number[],
  buys: Map<number, number>,
  unit: StreakUnit | null,
  round: (n: number) => number = roundWon,
): SimulationResult {
  const first = Math.min(...buys.keys());
  let units = 0;
  let invested = 0;
  let index = 1;
  let peak = { index: 1, date: iso(days[first]) };
  let maxDrawdown = { rate: 0, peak: iso(days[first]), trough: iso(days[first]) };
  let worstDay = { date: iso(days[first]), dailyReturn: 0 };
  let peakProfit = { amount: 0, date: iso(days[first]) };
  let maxDrawdownAmount = { amount: 0, peak: iso(days[first]), trough: iso(days[first]) };
  let worstDayAmount: { date: IsoDate; pnl: number } | null = null;
  let underwaterStart: IsoDate | null = null;
  let underwater: SimulationResult["pain"]["underwater"] = null;
  const out: SimulationDay[] = [];
  let prevValue = 0;
  const dayDiff = (a: IsoDate, b: IsoDate) => Math.round((Date.parse(b) - Date.parse(a)) / 86_400_000);

  for (let i = first; i < days.length; i++) {
    const date = iso(days[i]);
    const cf = buys.get(i) ?? 0;
    units += cf / closes[i];
    invested += cf;
    const value = units * closes[i];
    const r = prevValue > 0 ? (value - cf) / prevValue - 1 : 0;
    index *= 1 + r;
    if (index > peak.index) peak = { index, date };
    const dd = index / peak.index - 1;
    if (dd < maxDrawdown.rate) maxDrawdown = { rate: dd, peak: peak.date, trough: date };
    if (r < worstDay.dailyReturn) worstDay = { date, dailyReturn: r };
    const profit = value - invested;
    if (profit > peakProfit.amount) peakProfit = { amount: profit, date };
    if (profit - peakProfit.amount < maxDrawdownAmount.amount) {
      maxDrawdownAmount = { amount: round(profit - peakProfit.amount), peak: peakProfit.date, trough: date };
    }
    const pnl = round(value - cf - prevValue);
    if (!worstDayAmount || pnl < worstDayAmount.pnl) worstDayAmount = { date, pnl };
    const under = value < invested;
    if (under && !underwaterStart) underwaterStart = date;
    if (!under && underwaterStart) {
      const days = dayDiff(underwaterStart, date);
      if (!underwater || days > underwater.days) underwater = { start: underwaterStart, end: date, recovered: true, days };
      underwaterStart = null;
    }
    out.push({ date, close: round(closes[i]), value: round(value), invested: round(invested), dailyReturn: r, purchased: cf > 0 });
    prevValue = value;
  }
  if (underwaterStart) {
    const end = out[out.length - 1].date;
    const days = dayDiff(underwaterStart, end);
    if (!underwater || days > underwater.days) underwater = { start: underwaterStart, end, recovered: false, days };
  }

  const last = out[out.length - 1];
  const span = dayDiff(out[0].date, last.date);
  return {
    summary: {
      finalValue: last.value,
      invested: last.invested,
      profit: round(last.value - last.invested),
      totalReturn: last.value / last.invested - 1,
      twr: index - 1,
      annualizedTwr: span > 0 ? index ** (365 / span) - 1 : 0,
    },
    pain: { maxDrawdown, maxDrawdownAmount, underwater, worstDay, worstDayAmount: worstDayAmount! },
    streaks: {
      gain: runs(out, (d) => d.dailyReturn > 0, (d) => d.date, (d) => d.date),
      loss: runs(out, (d) => d.dailyReturn < 0, (d) => d.date, (d) => d.date),
      purchase: unit ? { unit, ...purchaseStreaks(out, unit) } : null,
    },
    days: out,
  };
}

export function mockSimulation(p: SimulationParams): SimulationResponse {
  const to = new Date(`${MOCK_LAST_DATE}T00:00:00Z`);
  const from = new Date(to);
  from.setUTCMonth(from.getUTCMonth() - { "6M": 6, "1Y": 12, "3Y": 36 }[p.period]);
  const days = weekdays(from, to);
  const buyIdx = purchaseIndexes(days, p);
  const perPurchase = p.amountType === "TOTAL" ? Math.floor(p.amount / buyIdx.length) : p.amount;
  const total = perPurchase * buyIdx.length;
  const currency = p.currency ?? "KRW";
  const fx = mockFx(days);
  // 달러 기준: 같은 원화 매수를 그날 환율로 나눈다
  const inBasis = (krw: number, i: number) => (currency === "USD" ? krw / fx[i] : krw);
  const round = currency === "USD" ? roundCents : roundWon;

  return {
    from: iso(days[0]),
    to: iso(days[days.length - 1]),
    currency,
    results: p.etfs.map((code) => {
      const etf = INSTRUMENTS.find((e) => e.code === code) ?? { ...INSTRUMENTS[0], code, name: code };
      const closes = prices(code, days).map(inBasis);
      return {
        etf: { code: etf.code, name: etf.name, market: etf.market, type: etf.type, currency: etf.currency },
        lumpSum: replay(days, closes, new Map([[0, inBasis(total, 0)]]), null, round),
        recurring: replay(
          days,
          closes,
          new Map(buyIdx.map((i) => [i, inBasis(perPurchase, i)])),
          ({ DAILY: "DAY", WEEKLY: "WEEK", MONTHLY: "MONTH" } as const)[p.frequency],
          round,
        ),
      };
    }),
    disclaimer: "과거 수익률은 미래 수익을 보장하지 않습니다. 이 계산은 투자 권유가 아닙니다.",
  };
}
