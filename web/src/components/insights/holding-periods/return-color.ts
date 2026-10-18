// 수익률 → 로그와 같은 초록/빨강 4단계 색.

/** 단계 경계(절댓값). 2% 미만 1단계, 10% 미만 2단계, 25% 미만 3단계, 그 이상 4단계 */
export const RETURN_STEPS = [0.02, 0.1, 0.25] as const;

export function returnLevel(rate: number): 1 | 2 | 3 | 4 {
  const abs = Math.abs(rate);
  return abs < RETURN_STEPS[0] ? 1 : abs < RETURN_STEPS[1] ? 2 : abs < RETURN_STEPS[2] ? 3 : 4;
}

export function returnColor(rate: number): string {
  if (rate === 0) return "var(--log-zero)";
  return `var(--${rate > 0 ? "gain" : "loss"}-${returnLevel(rate)})`;
}
