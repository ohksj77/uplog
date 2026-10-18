// 배당·비용 화면이 함께 쓰는 문장용 금액 표기. 금액이 작아 만 단위 소수 한 자리까지 보여준다.

/** 24,000 → "2.4만 원", 290,000 → "29만 원", 8,000 → "8,000원" */
export function formatWonTenth(amount: number): string {
  const abs = Math.abs(amount);
  if (abs < 10_000) return `${Math.round(abs).toLocaleString("ko-KR")}원`;
  const man = abs / 10_000;
  const text = man >= 100 ? Math.round(man).toLocaleString("ko-KR") : String(Math.round(man * 10) / 10);
  return `${text}만 원`;
}

/** 축·괄호 안 짧은 표기: 33,000 → "3.3만", 8,000 → "8,000" */
export function formatManShort(amount: number): string {
  return formatWonTenth(amount).replace(/ ?원$/, "");
}

/** 부호 없는 비율: 0.0084 → "0.84%" */
export function formatRate(rate: number, digits = 2): string {
  return `${(rate * 100).toFixed(digits)}%`;
}

/** "2026-03" → "2026년 3월" */
export function formatMonth(month: string): string {
  return `${month.slice(0, 4)}년 ${Number(month.slice(5, 7))}월`;
}
