import type { Currency, Market } from "@/lib/api/portfolio-types";
import { formatWon } from "@/lib/format";

// 종목 화면 표시 형식. 원화 종목은 원, 해외 종목은 현지 통화(달러).

const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });

export function formatLocal(value: number, currency: Currency): string {
  return currency === "USD" ? usd.format(value) : formatWon(value);
}

const fx = new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 2 });

/** 1342.1 → "1,342.1원" */
export function formatFx(rate: number): string {
  return `${fx.format(rate)}원`;
}

export const MARKET_LABEL: Record<Market, string> = { KR: "국내", US: "미국" };

/** 두 날짜 사이 달력일 */
export function daysBetween(a: string, b: string): number {
  return Math.round((Date.parse(b) - Date.parse(a)) / 86_400_000);
}

/** "2026-02-11" → "26.02.11" (좁은 축 라벨용) */
export function formatDateShort(date: string): string {
  return date.slice(2).replaceAll("-", ".");
}
