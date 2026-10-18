// docs/api/simulation.md 계약의 타입. 계약을 바꾸면 이 파일도 함께 고친다.

export type IsoDate = string; // YYYY-MM-DD

export type Market = "KR" | "US";

/** 통화. 종목의 거래 통화이자 시뮬레이션 결과 금액의 기준 통화 */
export type Currency = "KRW" | "USD";
export type InstrumentType = "ETF" | "STOCK";

/** 시뮬레이션 종목(실제 ETF·주식). 이름은 v1의 Etf를 그대로 쓴다. */
export type Etf = {
  code: string;
  name: string;
  market: Market;
  type: InstrumentType;
  /** 거래 통화. 응답 금액은 종목 통화와 상관없이 SimulationResponse.currency 기준 */
  currency: Currency;
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
  amount: number; // 원(매수는 늘 원화)
  /** 결과 금액의 기준 통화. 없으면 KRW. URL에는 USD일 때만 currency=USD로 남는다 */
  currency?: Currency;
};

export type SimulationDay = {
  date: IsoDate;
  /** 기준 통화 종가. 원화 기준이면 USD 종목은 그날 환율로 환산, 달러 기준이면 USD 종목은 달러 종가·국내 종목은 환율로 나눈 값 */
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
    /** 금액 기준: 평가손익 고점 대비 감소액(기준 통화, 0 이하) */
    maxDrawdownAmount: { amount: number; peak: IsoDate; trough: IsoDate };
    underwater: { start: IsoDate; end: IsoDate; recovered: boolean; days: number } | null;
    worstDay: { date: IsoDate; dailyReturn: number };
    /** 금액 기준: 하루 손익(기준 통화, 입출금 제외) */
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

/**
 * 금액은 currency 기준: KRW는 원 단위 정수, USD는 소수 둘째 자리 달러.
 * USD 기준은 같은 매수(같은 날 같은 원화 금액)를 그날 환율로 달러로 나타내 환율 효과를 뺀 결과다.
 */
export type SimulationResponse = {
  from: IsoDate;
  to: IsoDate;
  currency: Currency;
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
