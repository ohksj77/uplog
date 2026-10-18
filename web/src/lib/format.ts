// 표시 형식. 이익 초록(+), 손실 빨강(−).

const won = new Intl.NumberFormat("ko-KR");

export function formatWon(amount: number): string {
  return `${won.format(Math.round(amount))}원`;
}

/** 1억 이상은 "1.2억", 1만 이상은 "1,234만". 카드·축 라벨용. */
export function formatWonShort(amount: number): string {
  const abs = Math.abs(amount);
  const sign = amount < 0 ? "-" : "";
  if (abs >= 100_000_000) return `${sign}${(abs / 100_000_000).toFixed(abs >= 1_000_000_000 ? 0 : 1)}억`;
  if (abs >= 10_000) return `${sign}${won.format(Math.round(abs / 10_000))}만`;
  return `${sign}${won.format(Math.round(abs))}`;
}

export function formatSignedWon(amount: number): string {
  const rounded = Math.round(amount);
  return `${rounded > 0 ? "+" : ""}${won.format(rounded)}원`;
}

/** 0.1234 → "+12.34%" */
export function formatPercent(rate: number, digits = 2): string {
  const v = rate * 100;
  const s = v.toFixed(digits);
  return `${v > 0 && Number(s) !== 0 ? "+" : ""}${s}%`;
}

/** "2026-10-16" → "2026.10.16" */
export function formatDate(date: string): string {
  return date.replaceAll("-", ".");
}

export type Tone = "gain" | "loss" | "flat";

export function toneOf(value: number): Tone {
  if (value > 0) return "gain";
  if (value < 0) return "loss";
  return "flat";
}

/** 문장 속 금액. 1만 원 이상은 "282만 원"처럼 어림하고, 그 아래는 원 단위 그대로. */
export function formatWonApprox(amount: number): string {
  const abs = Math.abs(amount);
  return abs >= 10_000 ? `${formatWonShort(abs)} 원` : formatWon(abs);
}
