import type { Holding, PortfolioDay, PortfolioLog, PortfolioSummary } from "@/lib/api/portfolio-types";

// 메인(수익 로그)·하루 상세 mock. 담당: home 영역. fixtures.ts 재료로 만든다.

export function mockSummary(): PortfolioSummary {
  throw new Error("mockSummary: 구현 예정");
}

export function mockLog(year: number | null): PortfolioLog {
  void year;
  throw new Error("mockLog: 구현 예정");
}

export function mockHoldings(): Holding[] {
  throw new Error("mockHoldings: 구현 예정");
}

export function mockDay(date: string): PortfolioDay {
  void date;
  throw new Error("mockDay: 구현 예정");
}
