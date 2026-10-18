// 차트 공용 스케일 계산. DOM과 무관한 순수 함수만 둔다.

export type LinearScale = {
  (value: number): number;
  domain: [number, number];
  ticks: number[];
};

/** 1·2·5 단위로 떨어지는 축 눈금. */
export function niceTicks(min: number, max: number, count = 4): number[] {
  if (!Number.isFinite(min) || !Number.isFinite(max)) return [];
  if (min === max) {
    const pad = Math.abs(min) * 0.1 || 1;
    min -= pad;
    max += pad;
  }
  const raw = (max - min) / Math.max(1, count);
  const mag = 10 ** Math.floor(Math.log10(raw));
  const norm = raw / mag;
  const step = (norm >= 5 ? 10 : norm >= 2 ? 5 : norm >= 1 ? 2 : 1) * mag;
  const start = Math.floor(min / step) * step;
  const end = Math.ceil(max / step) * step;
  const ticks: number[] = [];
  for (let v = start; v <= end + step / 2; v += step) ticks.push(Number(v.toPrecision(12)));
  return ticks;
}

/** 눈금 끝까지 도메인을 넓힌 선형 스케일. */
export function linearScale(min: number, max: number, range: [number, number], count = 4): LinearScale {
  const ticks = niceTicks(min, max, count);
  const d0 = ticks[0] ?? min;
  const d1 = ticks[ticks.length - 1] ?? max;
  const [r0, r1] = range;
  const span = d1 - d0 || 1;
  const scale = ((v: number) => r0 + ((v - d0) / span) * (r1 - r0)) as LinearScale;
  scale.domain = [d0, d1];
  scale.ticks = ticks;
  return scale;
}

/** 0..n-1 인덱스 중 고르게 퍼진 count개(처음·끝 포함). */
export function spreadIndexes(n: number, count: number): number[] {
  if (n <= 0) return [];
  if (n <= count) return Array.from({ length: n }, (_, i) => i);
  const out = new Set<number>();
  for (let k = 0; k < count; k++) out.add(Math.round((k * (n - 1)) / (count - 1)));
  return [...out];
}
