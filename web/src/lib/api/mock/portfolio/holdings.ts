import type {
  FxInsight,
  HoldingDetail,
  HoldingPeriodsInsight,
  SaleHorizon,
  SalesInsight,
} from "@/lib/api/portfolio-types";

// 종목 상세·판 뒤의 그림자·보유 기간·수익 분해 mock. 담당: holdings 영역. fixtures.ts 재료로 만든다.

export function mockHoldingDetail(code: string): HoldingDetail {
  void code;
  throw new Error("mockHoldingDetail: 구현 예정");
}

export function mockSales(horizon: SaleHorizon): SalesInsight {
  void horizon;
  throw new Error("mockSales: 구현 예정");
}

export function mockHoldingPeriods(): HoldingPeriodsInsight {
  throw new Error("mockHoldingPeriods: 구현 예정");
}

export function mockFx(): FxInsight {
  throw new Error("mockFx: 구현 예정");
}
