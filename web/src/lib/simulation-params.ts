import type { AmountType, Frequency, Period, SimulationParams, Weekday } from "@/lib/api/types";

// 결과 화면 URL과 API 요청이 같은 쿼리 문자열을 쓴다. 공유 링크는 결과가 아니라 이 파라미터를 담는다.

const PERIODS: Period[] = ["6M", "1Y", "3Y"];
const FREQUENCIES: Frequency[] = ["DAILY", "WEEKLY", "MONTHLY"];
const WEEKDAYS: Weekday[] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"];
const AMOUNT_TYPES: AmountType[] = ["TOTAL", "PER_PURCHASE"];

export const MAX_ETFS = 3;
export const MIN_AMOUNT = 1_000;
export const MAX_AMOUNT = 1_000_000_000;

export const DEFAULT_PARAMS: SimulationParams = {
  etfs: [],
  period: "1Y",
  frequency: "MONTHLY",
  dayOfMonth: 1,
  amountType: "PER_PURCHASE",
  amount: 300_000,
};

export function toSearchParams(p: SimulationParams): URLSearchParams {
  const q = new URLSearchParams();
  q.set("etfs", p.etfs.join(","));
  q.set("period", p.period);
  q.set("frequency", p.frequency);
  if (p.frequency === "WEEKLY" && p.dayOfWeek) q.set("dayOfWeek", p.dayOfWeek);
  if (p.frequency === "MONTHLY" && p.dayOfMonth) q.set("dayOfMonth", String(p.dayOfMonth));
  q.set("amountType", p.amountType);
  q.set("amount", String(p.amount));
  return q;
}

/** 잘못되거나 빠진 값이 있으면 이유를 담아 돌려준다. */
export function parseSearchParams(
  q: URLSearchParams,
): { ok: true; params: SimulationParams } | { ok: false; error: string } {
  const etfs = (q.get("etfs") ?? "").split(",").map((s) => s.trim()).filter(Boolean);
  if (etfs.length === 0) return { ok: false, error: "ETF를 하나 이상 골라 주세요." };
  if (etfs.length > MAX_ETFS) return { ok: false, error: `ETF는 최대 ${MAX_ETFS}개까지 비교할 수 있어요.` };

  const period = q.get("period") as Period;
  if (!PERIODS.includes(period)) return { ok: false, error: "기간이 올바르지 않아요." };

  const frequency = q.get("frequency") as Frequency;
  if (!FREQUENCIES.includes(frequency)) return { ok: false, error: "적립 주기가 올바르지 않아요." };

  const amountType = q.get("amountType") as AmountType;
  if (!AMOUNT_TYPES.includes(amountType)) return { ok: false, error: "금액 방식이 올바르지 않아요." };

  const amount = Number(q.get("amount"));
  if (!Number.isInteger(amount) || amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
    return { ok: false, error: "금액은 1,000원 이상 10억 원 이하로 입력해 주세요." };
  }

  const params: SimulationParams = { etfs, period, frequency, amountType, amount };
  if (frequency === "WEEKLY") {
    const dayOfWeek = q.get("dayOfWeek") as Weekday;
    if (!WEEKDAYS.includes(dayOfWeek)) return { ok: false, error: "매수 요일을 골라 주세요." };
    params.dayOfWeek = dayOfWeek;
  }
  if (frequency === "MONTHLY") {
    const dayOfMonth = Number(q.get("dayOfMonth"));
    if (!Number.isInteger(dayOfMonth) || dayOfMonth < 1 || dayOfMonth > 31) {
      return { ok: false, error: "매수 날짜는 1–31일 중에서 골라 주세요." };
    }
    params.dayOfMonth = dayOfMonth;
  }
  return { ok: true, params };
}
