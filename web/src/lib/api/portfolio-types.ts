// docs/api/portfolio.md 계약의 타입. 계약을 바꾸면 이 파일도 함께 고친다.
import type { IsoDate, StreakStats } from "@/lib/api/types";

export type Market = "KR" | "US";
export type Currency = "KRW" | "USD";

export type Instrument = { code: string; name: string; market: Market; currency: Currency };

export type Trade = {
  date: IsoDate;
  instrument: Instrument;
  side: "BUY" | "SELL";
  quantity: number;
  priceLocal: number;
  /** 원화 종목은 1 */
  fxRate: number;
  /** 원, 수량 × 가격 × 환율 */
  amount: number;
  fee: number;
  tax: number;
};

export type PortfolioSummary = {
  account: { name: string; broker: "TOSS"; sample: boolean; firstDate: IsoDate; lastDate: IsoDate };
  asOf: IsoDate;
  totalValue: number;
  netDeposits: number;
  profit: number;
  twr: number;
  todayPnl: number;
  todayReturn: number;
};

export type LogDaySource = "COLLECTED" | "BACKFILLED" | "MISSING";

export type PortfolioLogDay = {
  date: IsoDate;
  value: number;
  netCashFlow: number;
  pnl: number;
  dailyReturn: number;
  source: LogDaySource;
};

export type PortfolioLog = {
  year: number;
  availableYears: number[];
  from: IsoDate;
  to: IsoDate;
  days: PortfolioLogDay[];
  stats: {
    gainDays: number;
    lossDays: number;
    flatDays: number;
    totalPnl: number;
    bestDay: { date: IsoDate; pnl: number } | null;
    worstDay: { date: IsoDate; pnl: number } | null;
  };
  streaks: { gain: StreakStats; loss: StreakStats };
};

export type Holding = {
  instrument: Instrument;
  quantity: number;
  avgCostLocal: number;
  priceLocal: number;
  fxRate: number;
  value: number;
  profit: number;
  returnRate: number;
  weight: number;
};

export type CashFlowItem = { type: "DEPOSIT" | "WITHDRAWAL" | "DIVIDEND"; amount: number; instrument: Instrument | null };

export type PortfolioDay = {
  date: IsoDate;
  prevDate: IsoDate | null;
  nextDate: IsoDate | null;
  value: number;
  netCashFlow: number;
  pnl: number;
  dailyReturn: number;
  contributions: { instrument: Instrument; pnl: number; dailyReturn: number; weight: number }[];
  trades: Trade[];
  cashFlows: CashFlowItem[];
};

export type PricePoint = { date: IsoDate; closeLocal: number };

export type SaleVerdict = "GOOD" | "BAD" | "PENDING";
export type SaleHorizon = 5 | 20 | 60;

export type Sale = {
  id: string;
  date: IsoDate;
  instrument: Instrument;
  quantity: number;
  priceLocal: number;
  fxRate: number;
  amount: number;
  realizedProfit: number;
  priceNowLocal: number;
  changeSinceSale: number;
  changeAfter: { d5: number | null; d20: number | null; d60: number | null };
  verdict: SaleVerdict;
  /** +면 피한 손실, −면 놓친 이익(원) */
  afterEffect: number;
  series: PricePoint[];
};

export type SalesInsight = {
  horizon: SaleHorizon;
  summary: { sales: number; judged: number; good: number; winRate: number; avoidedLoss: number; missedGain: number };
  sales: Sale[];
};

export type BenchmarkCode = "SPX" | "KOSPI200";

export type ShadowInsight = {
  benchmarks: { code: BenchmarkCode; name: string }[];
  benchmark: BenchmarkCode;
  series: { date: IsoDate; actual: number; shadow: number; netDeposits: number }[];
  summary: { actual: number; shadow: number; difference: number; actualTwr: number; shadowTwr: number };
};

export type WaterfallPeriod = "1M" | "3M" | "YTD" | "1Y" | "ALL";
export type WaterfallKind = "INSTRUMENT" | "DIVIDEND" | "FEE" | "TAX" | "FX" | "OTHER";

export type WaterfallInsight = {
  period: WaterfallPeriod;
  from: IsoDate;
  to: IsoDate;
  startValue: number;
  netCashFlow: number;
  endValue: number;
  items: { kind: WaterfallKind; label: string; instrument: Instrument | null; amount: number }[];
  total: number;
};

export type HoldingPeriod = {
  id: string;
  instrument: Instrument;
  open: IsoDate;
  close: IsoDate | null;
  days: number;
  returnRate: number;
  profit: number;
  status: "CLOSED" | "OPEN";
};

export type HoldingBucketKey = "SHORT" | "SWING" | "MID" | "LONG";

export type HoldingPeriodsInsight = {
  periods: HoldingPeriod[];
  buckets: { key: HoldingBucketKey; label: string; count: number; winRate: number; avgReturn: number; profit: number }[];
};

export type FxItem = {
  instrument: Instrument;
  quantity: number;
  avgCostLocal: number;
  avgFxRate: number;
  priceLocal: number;
  fxRate: number;
  priceEffect: number;
  fxEffect: number;
  profit: number;
};

export type FxInsight = {
  asOf: IsoDate;
  items: FxItem[];
  total: { priceEffect: number; fxEffect: number; profit: number };
};

export type HoldingDetail = {
  instrument: Instrument;
  position: Omit<Holding, "instrument" | "weight"> | null;
  realizedProfit: number;
  dividends: number;
  prices: PricePoint[];
  trades: Trade[];
  periods: HoldingPeriod[];
  sales: Sale[];
  fx: FxItem | null;
};

export type UnderwaterInsight = {
  series: { date: IsoDate; drawdown: number; drawdownAmount: number }[];
  episodes: {
    peak: IsoDate;
    trough: IsoDate;
    recovered: IsoDate | null;
    depth: number;
    daysToTrough: number;
    daysToRecover: number | null;
  }[];
  current: { drawdown: number; peak: IsoDate; days: number; deepestPast: number; deeperThanPast: boolean };
};
