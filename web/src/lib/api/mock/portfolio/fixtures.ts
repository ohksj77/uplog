import type { IsoDate } from "@/lib/api/types";
import type { Instrument, PricePoint } from "@/lib/api/portfolio-types";

// 기능 B mock 공용 재료. 모두 합성 데이터이고 실제 종목·시세가 아니다.
// 화면 영역별 mock(home.ts, holdings.ts, market.ts)이 이 재료로 응답을 만든다.

export const MOCK_FIRST_DATE = "2024-10-14";
export const MOCK_LAST_DATE = "2026-10-16";

export const INSTRUMENTS: Instrument[] = [
  { code: "S00001", name: "샘플전자", market: "KR", currency: "KRW" },
  { code: "S00002", name: "샘플바이오", market: "KR", currency: "KRW" },
  { code: "S00003", name: "샘플 코스피200 ETF", market: "KR", currency: "KRW" },
  { code: "S00004", name: "샘플화학", market: "KR", currency: "KRW" },
  { code: "SMPT", name: "Sample Tech", market: "US", currency: "USD" },
  { code: "SMPC", name: "Sample Chips", market: "US", currency: "USD" },
  { code: "SMPS", name: "Sample S&P500 ETF", market: "US", currency: "USD" },
  { code: "SMPE", name: "Sample Energy", market: "US", currency: "USD" },
];

export function instrument(code: string): Instrument | undefined {
  return INSTRUMENTS.find((i) => i.code === code);
}

/** 결정적 난수. 같은 시드면 항상 같은 값. */
export function rng(seedText: string): () => number {
  let seed = [...seedText].reduce((a, c) => (a * 31 + c.charCodeAt(0)) >>> 0, 7);
  return () => (seed = (seed * 1664525 + 1013904223) >>> 0) / 2 ** 32;
}

export const isoOf = (d: Date): IsoDate => d.toISOString().slice(0, 10);

/** [from, to] 평일. 휴장일은 없다고 본다. */
export function weekdays(from: IsoDate = MOCK_FIRST_DATE, to: IsoDate = MOCK_LAST_DATE): IsoDate[] {
  const out: IsoDate[] = [];
  for (const d = new Date(`${from}T00:00:00Z`); isoOf(d) <= to; d.setUTCDate(d.getUTCDate() + 1)) {
    const dow = d.getUTCDay();
    if (dow !== 0 && dow !== 6) out.push(isoOf(d));
  }
  return out;
}

const cache = new Map<string, PricePoint[]>();

/** 종목(또는 "USDKRW", "SPX", "KOSPI200")의 일별 종가. 현지 통화. */
export function priceSeries(code: string): PricePoint[] {
  const hit = cache.get(code);
  if (hit) return hit;
  const rand = rng(code);
  const usd = instrument(code)?.currency === "USD" || code === "SPX";
  let p = code === "USDKRW" ? 1320 : code === "SPX" ? 5800 : code === "KOSPI200" ? 340 : usd ? 40 + rand() * 260 : 10_000 + rand() * 90_000;
  const vol = code === "USDKRW" ? 0.003 : code === "SPX" || code === "KOSPI200" ? 0.009 : 0.012 + rand() * 0.012;
  const drift = code === "USDKRW" ? 0.00005 : 0.0003 + (rand() - 0.4) * 0.0008;
  const series = weekdays().map((date) => {
    p *= 1 + drift + vol * (rand() + rand() + rand() - 1.5) * 1.4;
    return { date, closeLocal: usd || code === "USDKRW" ? Math.round(p * 100) / 100 : Math.round(p) };
  });
  cache.set(code, series);
  return series;
}

export function closeOn(code: string, date: IsoDate): number {
  const s = priceSeries(code);
  let lo = 0;
  let hi = s.length - 1;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    if (s[mid].date <= date) lo = mid;
    else hi = mid - 1;
  }
  return s[lo].closeLocal;
}

export type MockAccountDay = { date: IsoDate; value: number; netCashFlow: number };

let account: MockAccountDay[] | null = null;

/** 계좌 평가금 시계열. 매달 첫 거래일 100만 원 입금, 가끔 출금. 화면이 이익·손실 문장을 모두 보이도록 완만한 상승 추세. */
export function accountSeries(): MockAccountDay[] {
  if (account) return account;
  const rand = rng("account");
  let value = 0;
  let month = "";
  account = weekdays().map((date) => {
    let cf = 0;
    if (date.slice(0, 7) !== month) {
      month = date.slice(0, 7);
      cf = value === 0 ? 10_000_000 : rand() < 0.08 ? -2_000_000 : 1_000_000;
    }
    value = Math.max(0, value * (1 + 0.0009 + 0.011 * (rand() + rand() + rand() - 1.5) * 1.4) + cf);
    return { date, value: Math.round(value), netCashFlow: cf };
  });
  return account;
}
