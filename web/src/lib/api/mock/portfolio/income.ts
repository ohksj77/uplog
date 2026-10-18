import type { CostsInsight, DividendsInsight } from "@/lib/api/portfolio-types";

// 배당 흐름·숨은 비용 mock. 담당: income 영역. fixtures.ts(그리고 필요하면 holdings.ts의 체결)로 만든다.

export function mockDividends(): DividendsInsight {
  throw new Error("mockDividends: 구현 예정");
}

export function mockCosts(year: number | null): CostsInsight {
  void year;
  throw new Error("mockCosts: 구현 예정");
}
