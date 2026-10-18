import type { IsoDate } from "@/lib/api/types";
import type {
  BenchmarkCode,
  ShadowInsight,
  UnderwaterInsight,
  WaterfallInsight,
  WaterfallPeriod,
} from "@/lib/api/portfolio-types";
import { accountSeries, closeOn, INSTRUMENTS, isoOf, rng } from "@/lib/api/mock/portfolio/fixtures";

// 그림자 계좌·손익 폭포수·수중 차트 mock. 담당: market 영역. fixtures.ts 재료로 만든다.

const BENCHMARKS: ShadowInsight["benchmarks"] = [
  { code: "SPX", name: "S&P500" },
  { code: "KOSPI200", name: "코스피200" },
];

const round4 = (v: number) => Math.round(v * 10_000) / 10_000;

const daysBetween = (a: IsoDate, b: IsoDate) => Math.round((Date.parse(b) - Date.parse(a)) / 86_400_000);

/** 하루 수익률. 계산 정의: (value_t − netCashFlow_t − value_{t−1}) / value_{t−1} */
function dailyReturns(): number[] {
  const days = accountSeries();
  return days.map((d, i) => {
    const prev = i === 0 ? 0 : days[i - 1].value;
    return prev === 0 ? 0 : (d.value - d.netCashFlow - prev) / prev;
  });
}

/** 지수의 원화 값. S&P500은 그날 원/달러 환율로 바꾼다. */
function benchmarkKrw(code: BenchmarkCode, date: IsoDate): number {
  return code === "SPX" ? closeOn("SPX", date) * closeOn("USDKRW", date) : closeOn("KOSPI200", date);
}

export function mockShadow(benchmark: BenchmarkCode): ShadowInsight {
  const days = accountSeries();
  let units = 0;
  let netDeposits = 0;
  const series = days.map((d) => {
    const price = benchmarkKrw(benchmark, d.date);
    // 입금은 그날 종가에 사고, 출금은 그날 종가에 판다.
    units = Math.max(0, units + d.netCashFlow / price);
    netDeposits += d.netCashFlow;
    return { date: d.date, actual: d.value, shadow: Math.round(units * price), netDeposits };
  });

  const last = series[series.length - 1];
  const actualTwr = dailyReturns().reduce((acc, r) => acc * (1 + r), 1) - 1;
  const shadowTwr = benchmarkKrw(benchmark, last.date) / benchmarkKrw(benchmark, series[0].date) - 1;
  return {
    benchmarks: BENCHMARKS,
    benchmark,
    series,
    summary: {
      actual: last.actual,
      shadow: last.shadow,
      difference: last.actual - last.shadow,
      actualTwr: round4(actualTwr),
      shadowTwr: round4(shadowTwr),
    },
  };
}

/** 기간 시작 직전 거래일의 인덱스. */
function periodStartIndex(period: WaterfallPeriod, dates: IsoDate[]): number {
  if (period === "ALL") return 0;
  const to = new Date(`${dates[dates.length - 1]}T00:00:00Z`);
  const cut = new Date(to);
  if (period === "1M") cut.setUTCMonth(cut.getUTCMonth() - 1);
  if (period === "3M") cut.setUTCMonth(cut.getUTCMonth() - 3);
  if (period === "1Y") cut.setUTCFullYear(cut.getUTCFullYear() - 1);
  if (period === "YTD") cut.setUTCFullYear(cut.getUTCFullYear(), 0, 0); // 전년 12월 31일
  const iso = isoOf(cut);
  let index = 0;
  dates.forEach((d, i) => {
    if (d <= iso) index = i;
  });
  return index;
}

export function mockWaterfall(period: WaterfallPeriod): WaterfallInsight {
  const days = accountSeries();
  const fromIndex = periodStartIndex(period, days.map((d) => d.date));
  const start = days[fromIndex];
  const end = days[days.length - 1];
  const netCashFlow = days.slice(fromIndex + 1).reduce((s, d) => s + d.netCashFlow, 0);
  const total = end.value - start.value - netCashFlow;

  const rand = rng(`waterfall:${period}`);
  const years = Math.max(1 / 12, daysBetween(start.date, end.date) / 365);
  const scale = Math.max(start.value, end.value);
  const dividend = Math.round(scale * 0.006 * years);
  const fee = -Math.round(scale * 0.0009 * years);
  const tax = -Math.round(scale * 0.0014 * years);
  const fx = Math.round(scale * (rand() - 0.35) * 0.03 * Math.sqrt(years));

  // 종목별 주가 효과: 무작위로 흩뿌린 뒤 합이 맞게 고르게 옮긴다.
  const rest = total - dividend - fee - tax - fx;
  const raw = INSTRUMENTS.map(() => (rand() - 0.4) * scale * 0.05 * Math.sqrt(years));
  const shift = (rest - raw.reduce((s, v) => s + v, 0)) / raw.length;
  const instruments = INSTRUMENTS.map((instrument, i) => ({
    kind: "INSTRUMENT" as const,
    label: instrument.name,
    instrument,
    amount: Math.round(raw[i] + shift),
  })).sort((a, b) => b.amount - a.amount);

  const items: WaterfallInsight["items"] = [
    ...instruments,
    { kind: "DIVIDEND", label: "배당", instrument: null, amount: dividend },
    { kind: "FEE", label: "수수료", instrument: null, amount: fee },
    { kind: "TAX", label: "세금", instrument: null, amount: tax },
    { kind: "FX", label: "환율", instrument: null, amount: fx },
  ];
  const residual = total - items.reduce((s, it) => s + it.amount, 0);
  if (residual !== 0) items.push({ kind: "OTHER", label: "기타", instrument: null, amount: residual });

  return {
    period,
    from: start.date,
    to: end.date,
    startValue: start.value,
    netCashFlow,
    endValue: end.value,
    items,
    total,
  };
}

export function mockUnderwater(): UnderwaterInsight {
  const days = accountSeries();
  const returns = dailyReturns();

  let index = 1;
  let peakIndex = 1;
  let peakDate = days[0].date;
  let deposits = 0;
  let peakProfit = -Infinity;

  type Episode = UnderwaterInsight["episodes"][number];
  const episodes: Episode[] = [];
  let open: { peak: IsoDate; trough: IsoDate; depth: number } | null = null;

  const series = days.map((d, i) => {
    index *= 1 + returns[i];
    deposits += d.netCashFlow;
    const profit = d.value - deposits;
    peakProfit = Math.max(peakProfit, profit);

    if (index >= peakIndex) {
      if (open) {
        episodes.push({
          peak: open.peak,
          trough: open.trough,
          recovered: d.date,
          depth: round4(open.depth),
          daysToTrough: daysBetween(open.peak, open.trough),
          daysToRecover: daysBetween(open.trough, d.date), // 계약: 저점부터 회복까지
        });
        open = null;
      }
      peakIndex = index;
      peakDate = d.date;
      return { date: d.date, drawdown: 0, drawdownAmount: profit - peakProfit };
    }

    const drawdown = index / peakIndex - 1;
    if (!open) open = { peak: peakDate, trough: d.date, depth: drawdown };
    else if (drawdown < open.depth) open = { ...open, trough: d.date, depth: drawdown };
    return { date: d.date, drawdown: round4(drawdown), drawdownAmount: profit - peakProfit };
  });

  const past = episodes.reduce((min, e) => Math.min(min, e.depth), 0);
  const last = series[series.length - 1];
  const unfinished = open as { peak: IsoDate; trough: IsoDate; depth: number } | null;
  if (unfinished) {
    episodes.push({
      peak: unfinished.peak,
      trough: unfinished.trough,
      recovered: null,
      depth: round4(unfinished.depth),
      daysToTrough: daysBetween(unfinished.peak, unfinished.trough),
      daysToRecover: null,
    });
  }
  episodes.sort((a, b) => a.depth - b.depth);

  const current = unfinished
    ? {
        drawdown: last.drawdown,
        peak: unfinished.peak,
        days: daysBetween(unfinished.peak, last.date),
        deepestPast: past,
        deeperThanPast: last.drawdown < past,
      }
    : { drawdown: 0, peak: last.date, days: 0, deepestPast: past, deeperThanPast: false };

  return { series, episodes, current };
}
