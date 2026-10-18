import type {
  BenchmarkCode,
  ShadowInsight,
  UnderwaterInsight,
  WaterfallInsight,
  WaterfallPeriod,
} from "@/lib/api/portfolio-types";

// 그림자 계좌·손익 폭포수·수중 차트 mock. 담당: market 영역. fixtures.ts 재료로 만든다.

export function mockShadow(benchmark: BenchmarkCode): ShadowInsight {
  void benchmark;
  throw new Error("mockShadow: 구현 예정");
}

export function mockWaterfall(period: WaterfallPeriod): WaterfallInsight {
  void period;
  throw new Error("mockWaterfall: 구현 예정");
}

export function mockUnderwater(): UnderwaterInsight {
  throw new Error("mockUnderwater: 구현 예정");
}
