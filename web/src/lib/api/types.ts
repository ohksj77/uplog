// docs/api/simulation.md 계약의 타입. 계약을 바꾸면 이 파일도 함께 고친다.

export type IsoDate = string; // YYYY-MM-DD

export type Market = "KR" | "US";
export type InstrumentType = "ETF" | "STOCK";

/** 시뮬레이션 종목(실제 ETF·주식). 이름은 v1의 Etf를 그대로 쓴다. */
export type Etf = {
  code: string;
  name: string;
  market: Market;
  type: InstrumentType;
  /** 거래 통화. 응답 금액은 통화와 상관없이 원화(USD 종목은 그날 환율로 환산) */
  currency: "KRW" | "USD";
};

export type EtfListItem = Etf & {
  /** 저장된 시세 구간. 아직 시세를 받지 않은 종목은 null(시뮬레이션할 때 받는다) */
  firstDate: IsoDate | null;
  lastDate: IsoDate | null;
};

export type Period = "6M" | "1Y" | "3Y";
export type Frequency = "DAILY" | "WEEKLY" | "MONTHLY";
export type Weekday = "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY";
export type AmountType = "TOTAL" | "PER_PURCHASE";

export type SimulationParams = {
  etfs: string[]; // 종목코드 1–3개
  period: Period;
  frequency: Frequency;
  dayOfWeek?: Weekday; // WEEKLY
  dayOfMonth?: number; // MONTHLY, 1–31
  amountType: AmountType;
  amount: number; // 원
};

export type SimulationDay = {
  date: IsoDate;
  /** 원화 종가(USD 종목은 그날 환율로 환산) */
  close: number;
  value: number;
  invested: number;
  dailyReturn: number;
  purchased: boolean;
};

export type Streak = { length: number; start: IsoDate; end: IsoDate };

export type StreakStats = { longest: Streak | null; current: Streak | null };

export type StreakUnit = "DAY" | "WEEK" | "MONTH";

export type SimulationResult = {
  summary: {
    finalValue: number;
    invested: number;
    profit: number;
    totalReturn: number;
    twr: number;
    annualizedTwr: number;
  };
  pain: {
    /** 수익률 기준(TWR) */
    maxDrawdown: { rate: number; peak: IsoDate; trough: IsoDate };
    /** 금액 기준: 평가손익 고점 대비 감소액(원, 0 이하) */
    maxDrawdownAmount: { amount: number; peak: IsoDate; trough: IsoDate };
    underwater: { start: IsoDate; end: IsoDate; recovered: boolean; days: number } | null;
    worstDay: { date: IsoDate; dailyReturn: number };
    /** 금액 기준: 하루 손익(원, 입출금 제외) */
    worstDayAmount: { date: IsoDate; pnl: number };
  };
  streaks: {
    gain: StreakStats;
    loss: StreakStats;
    /** 거치식은 null */
    purchase: (StreakStats & { unit: StreakUnit }) | null;
  };
  days: SimulationDay[];
};

export type SimulationResponse = {
  from: IsoDate;
  to: IsoDate;
  results: { etf: Etf; lumpSum: SimulationResult; recurring: SimulationResult }[];
  disclaimer: string;
};

export type ApiErrorBody = {
  code:
    | "INVALID_PARAMETER"
    | "SIMULATION_REQUIRES_CONNECTION"
    | "ETF_NOT_FOUND"
    | "NO_PRICE_DATA"
    | "MARKET_DATA_UNAVAILABLE"
    | string;
  message: string;
};
