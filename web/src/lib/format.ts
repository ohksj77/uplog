// 표시 형식. 이익 빨강(+), 손실 파랑(−).

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

// ── 기준 통화(시뮬레이션 원화 / 달러 기준) ──────────────────────────────

/** 금액을 나타내는 통화. 시뮬레이션 응답의 currency와 같다. */
export type MoneyCurrency = "KRW" | "USD";

const usd = new Intl.NumberFormat("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** 1234.5 → "$1,234.50", -12.3 → "-$12.30" */
export function formatUsd(amount: number): string {
  const cents = Math.round(amount * 100) / 100;
  return `${cents < 0 ? "-" : ""}$${usd.format(Math.abs(cents))}`;
}

/** 원화면 "1,234원", 달러면 "$1,234.56". */
export function formatMoney(amount: number, currency: MoneyCurrency): string {
  return currency === "USD" ? formatUsd(amount) : formatWon(amount);
}

/**
 * 카드·축 라벨용 짧은 금액. 원화는 formatWonShort 그대로("1.2억", "1,234만", 단위 없음 — unit이면 "원"을 붙인다),
 * 달러는 "$1.2M", "$1.2K", "$589"(달러 기호가 단위라 unit과 상관없이 붙는다).
 */
export function formatMoneyShort(amount: number, currency: MoneyCurrency, { unit = false } = {}): string {
  if (currency === "KRW") return `${formatWonShort(amount)}${unit ? "원" : ""}`;
  const abs = Math.abs(amount);
  const sign = amount < 0 ? "-" : "";
  const short = (v: number) => v.toFixed(1).replace(/\.0$/, "");
  if (abs >= 1_000_000) return `${sign}$${short(abs / 1_000_000)}M`;
  if (abs >= 1_000) return `${sign}$${short(abs / 1_000)}K`;
  return `${sign}$${Math.round(abs)}`;
}

/** 원화면 "+1,234원", 달러면 "+$1,234.56" / "-$12.30". 0은 부호 없음. */
export function formatSignedMoney(amount: number, currency: MoneyCurrency): string {
  if (currency === "KRW") return formatSignedWon(amount);
  const cents = Math.round(amount * 100) / 100;
  return `${cents > 0 ? "+" : ""}${formatUsd(cents)}`;
}
