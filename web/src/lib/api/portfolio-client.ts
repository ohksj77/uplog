import { get, USE_MOCK } from "@/lib/api/client";
import type {
  BenchmarkCode,
  FxInsight,
  Holding,
  HoldingDetail,
  HoldingPeriodsInsight,
  PortfolioDay,
  PortfolioLog,
  PortfolioSummary,
  SaleHorizon,
  SalesInsight,
  ShadowInsight,
  UnderwaterInsight,
  WaterfallInsight,
  WaterfallPeriod,
} from "@/lib/api/portfolio-types";
import * as home from "@/lib/api/mock/portfolio/home";
import * as holdings from "@/lib/api/mock/portfolio/holdings";
import * as market from "@/lib/api/mock/portfolio/market";

// 기능 B API (docs/api/portfolio.md). mock은 화면 영역별 파일에 나눠 둔다.

/** mock이 던진 오류(404 등)도 실제 요청처럼 거부된 Promise로 돌려준다. */
function mock<T>(make: () => T): Promise<T> {
  return Promise.resolve().then(make);
}

export function getPortfolioSummary(signal?: AbortSignal): Promise<PortfolioSummary> {
  if (USE_MOCK) return mock(() => home.mockSummary());
  return get("/api/portfolio/summary", signal);
}

export function getPortfolioLog(year: number | null, signal?: AbortSignal): Promise<PortfolioLog> {
  if (USE_MOCK) return mock(() => home.mockLog(year));
  return get(`/api/portfolio/log${year ? `?year=${year}` : ""}`, signal);
}

export function getHoldings(signal?: AbortSignal): Promise<Holding[]> {
  if (USE_MOCK) return mock(() => home.mockHoldings());
  return get("/api/portfolio/holdings", signal);
}

export function getPortfolioDay(date: string, signal?: AbortSignal): Promise<PortfolioDay> {
  if (USE_MOCK) return mock(() => home.mockDay(date));
  return get(`/api/portfolio/days/${encodeURIComponent(date)}`, signal);
}

export function getHoldingDetail(code: string, signal?: AbortSignal): Promise<HoldingDetail> {
  if (USE_MOCK) return mock(() => holdings.mockHoldingDetail(code));
  return get(`/api/portfolio/holdings/${encodeURIComponent(code)}`, signal);
}

export function getSalesInsight(horizon: SaleHorizon, signal?: AbortSignal): Promise<SalesInsight> {
  if (USE_MOCK) return mock(() => holdings.mockSales(horizon));
  return get(`/api/portfolio/insights/sales?horizon=${horizon}`, signal);
}

export function getHoldingPeriodsInsight(signal?: AbortSignal): Promise<HoldingPeriodsInsight> {
  if (USE_MOCK) return mock(() => holdings.mockHoldingPeriods());
  return get("/api/portfolio/insights/holding-periods", signal);
}

export function getFxInsight(signal?: AbortSignal): Promise<FxInsight> {
  if (USE_MOCK) return mock(() => holdings.mockFx());
  return get("/api/portfolio/insights/fx", signal);
}

export function getShadowInsight(benchmark: BenchmarkCode, signal?: AbortSignal): Promise<ShadowInsight> {
  if (USE_MOCK) return mock(() => market.mockShadow(benchmark));
  return get(`/api/portfolio/insights/shadow?benchmark=${benchmark}`, signal);
}

export function getWaterfallInsight(period: WaterfallPeriod, signal?: AbortSignal): Promise<WaterfallInsight> {
  if (USE_MOCK) return mock(() => market.mockWaterfall(period));
  return get(`/api/portfolio/insights/waterfall?period=${period}`, signal);
}

export function getUnderwaterInsight(signal?: AbortSignal): Promise<UnderwaterInsight> {
  if (USE_MOCK) return mock(() => market.mockUnderwater());
  return get("/api/portfolio/insights/underwater", signal);
}
