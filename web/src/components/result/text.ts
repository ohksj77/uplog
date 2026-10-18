import type { Period, SimulationParams, SimulationResult, Weekday } from "@/lib/api/types";
import { formatWon, formatWonShort } from "@/lib/format";

// 결과 화면 문장 만들기. 판단·권유 없이 사실만 말한다.

export type Strategy = "lumpSum" | "recurring";

export const STRATEGY_LABEL: Record<Strategy, string> = {
  lumpSum: "거치식",
  recurring: "적립식",
};

export const STRATEGIES: Strategy[] = ["lumpSum", "recurring"];

export const PERIOD_LABEL: Record<Period, string> = { "6M": "6개월", "1Y": "1년", "3Y": "3년" };

const WEEKDAY_LABEL: Record<Weekday, string> = {
  MONDAY: "월요일",
  TUESDAY: "화요일",
  WEDNESDAY: "수요일",
  THURSDAY: "목요일",
  FRIDAY: "금요일",
};

/** 문장용 금액. 만 원 단위로 떨어지면 "30만 원", 아니면 "12,345원". */
export function wonWords(amount: number): string {
  const abs = Math.abs(Math.round(amount));
  if (abs >= 10_000 && abs % 10_000 === 0) return `${formatWonShort(abs)} 원`;
  if (abs >= 100_000_000) return `${formatWonShort(abs)} 원`;
  return formatWon(abs);
}

/** "매월 15일 30만 원씩 적립", "매주 월요일 총 300만 원을 나눠 적립" */
export function describePlan(p: SimulationParams): string {
  const when =
    p.frequency === "DAILY"
      ? "매 거래일"
      : p.frequency === "WEEKLY"
        ? `매주 ${WEEKDAY_LABEL[p.dayOfWeek ?? "MONDAY"]}`
        : `매월 ${p.dayOfMonth}일`;
  return p.amountType === "PER_PURCHASE"
    ? `${when} ${wonWords(p.amount)}씩 적립`
    : `${when} 총 ${wonWords(p.amount)}을 나눠 적립`;
}

/** 두 방식의 손익을 비교하는 한 문장. */
export function verdict(lumpSum: SimulationResult, recurring: SimulationResult): string {
  const a = { name: "적립식", profit: recurring.summary.profit };
  const b = { name: "거치식", profit: lumpSum.summary.profit };
  const diff = Math.round(a.profit - b.profit);
  if (diff === 0) return "같은 기간 두 방식의 손익이 같았어요.";
  const [better, worse] = diff > 0 ? [a, b] : [b, a];
  const gap = wonWords(Math.abs(diff));
  if (better.profit > 0 && worse.profit >= 0) return `같은 기간 ${better.name}이 ${worse.name}보다 ${gap} 더 벌었어요.`;
  if (better.profit > 0) {
    return `같은 기간 ${better.name}은 ${wonWords(better.profit)} 벌었고, ${worse.name}은 ${wonWords(worse.profit)} 잃었어요.`;
  }
  if (better.profit === 0) return `같은 기간 ${better.name}은 본전이었고, ${worse.name}은 ${wonWords(worse.profit)} 잃었어요.`;
  return `같은 기간 ${better.name}이 ${worse.name}보다 ${gap} 덜 잃었어요.`;
}

/** 두 날짜 사이 일수 */
export function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(to) - Date.parse(from)) / 86_400_000);
}
