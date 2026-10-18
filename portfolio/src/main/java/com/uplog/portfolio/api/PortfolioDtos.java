package com.uplog.portfolio.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * docs/api/portfolio.md의 JSON 형태. 필드 이름은 web/src/lib/api/portfolio-types.ts와 같다.
 * 금액은 원 단위 반올림한 정수(long), {@code …Local}과 수량은 소수(double), 비율은 소수(double), 없는 값은 null.
 */
public final class PortfolioDtos {

    private PortfolioDtos() {}

    public record ErrorResponse(String code, String message) {}

    /** @param market KR | US, currency KRW | USD */
    public record InstrumentDto(String code, String name, String market, String currency) {}

    public record Streak(int length, LocalDate start, LocalDate end) {}

    public record StreakStats(Streak longest, Streak current) {}

    /** @param amount 원 = 수량 × 가격 × 환율, fee·tax 원 */
    public record TradeDto(
            LocalDate date, InstrumentDto instrument, String side, double quantity, double priceLocal, double fxRate,
            long amount, long fee, long tax) {}

    public record PricePoint(LocalDate date, double closeLocal) {}

    // GET /api/portfolio/summary

    public record AccountDto(String name, String broker, boolean sample, LocalDate firstDate, LocalDate lastDate) {}

    public record SummaryResponse(
            AccountDto account, LocalDate asOf, long totalValue, long netDeposits, long profit, double twr,
            long todayPnl, double todayReturn) {}

    // GET /api/portfolio/log

    public record LogDay(LocalDate date, long value, long netCashFlow, long pnl, double dailyReturn, String source) {}

    public record DayPnl(LocalDate date, long pnl) {}

    public record LogStats(int gainDays, int lossDays, int flatDays, long totalPnl, DayPnl bestDay, DayPnl worstDay) {}

    public record LogStreaks(StreakStats gain, StreakStats loss) {}

    public record LogResponse(
            int year, List<Integer> availableYears, LocalDate from, LocalDate to, List<LogDay> days, LogStats stats,
            LogStreaks streaks) {}

    // GET /api/portfolio/holdings, /holdings/{code}

    public record HoldingDto(
            InstrumentDto instrument, double quantity, double avgCostLocal, double priceLocal, double fxRate, long value,
            long profit, double returnRate, double weight) {}

    public record PositionDto(
            double quantity, double avgCostLocal, double priceLocal, double fxRate, long value, long profit, double returnRate) {}

    public record HoldingDetailResponse(
            InstrumentDto instrument, PositionDto position, long realizedProfit, long dividends, List<PricePoint> prices,
            List<TradeDto> trades, List<HoldingPeriodDto> periods, List<SaleDto> sales, FxItemDto fx) {}

    // GET /api/portfolio/days/{date}

    public record ContributionDto(InstrumentDto instrument, long pnl, double dailyReturn, double weight) {}

    /** @param type DEPOSIT | WITHDRAWAL | DIVIDEND, amount 항상 양수(원), instrument 배당일 때만 */
    public record CashFlowDto(String type, long amount, InstrumentDto instrument) {}

    public record DayResponse(
            LocalDate date, LocalDate prevDate, LocalDate nextDate, long value, long netCashFlow, long pnl, double dailyReturn,
            List<ContributionDto> contributions, List<TradeDto> trades, List<CashFlowDto> cashFlows) {}

    // 1. GET /api/portfolio/insights/sales

    public record ChangeAfter(Double d5, Double d20, Double d60) {}

    public record SaleDto(
            String id, LocalDate date, InstrumentDto instrument, double quantity, double priceLocal, double fxRate, long amount,
            long realizedProfit, double priceNowLocal, double changeSinceSale, ChangeAfter changeAfter, String verdict,
            long afterEffect, List<PricePoint> series) {}

    public record SalesSummary(int sales, int judged, int good, double winRate, long avoidedLoss, long missedGain) {}

    public record SalesResponse(int horizon, SalesSummary summary, List<SaleDto> sales) {}

    // 2. GET /api/portfolio/insights/shadow

    public record BenchmarkDto(String code, String name) {}

    public record ShadowPoint(LocalDate date, long actual, long shadow, long netDeposits) {}

    public record ShadowSummary(long actual, long shadow, long difference, double actualTwr, double shadowTwr) {}

    public record ShadowResponse(
            List<BenchmarkDto> benchmarks, String benchmark, List<ShadowPoint> series, ShadowSummary summary) {}

    // 3. GET /api/portfolio/insights/waterfall

    public record WaterfallItem(String kind, String label, InstrumentDto instrument, long amount) {}

    public record WaterfallResponse(
            String period, LocalDate from, LocalDate to, long startValue, long netCashFlow, long endValue,
            List<WaterfallItem> items, long total) {}

    // 4. GET /api/portfolio/insights/holding-periods

    /** @param status CLOSED | OPEN */
    public record HoldingPeriodDto(
            String id, InstrumentDto instrument, LocalDate open, LocalDate close, long days, double returnRate, long profit,
            String status) {}

    public record BucketDto(String key, String label, int count, double winRate, double avgReturn, long profit) {}

    public record HoldingPeriodsResponse(List<HoldingPeriodDto> periods, List<BucketDto> buckets) {}

    // 5. GET /api/portfolio/insights/fx

    public record FxItemDto(
            InstrumentDto instrument, double quantity, double avgCostLocal, double avgFxRate, double priceLocal, double fxRate,
            long priceEffect, long fxEffect, long profit) {}

    public record FxTotal(long priceEffect, long fxEffect, long profit) {}

    public record FxResponse(LocalDate asOf, List<FxItemDto> items, FxTotal total) {}

    // 6. GET /api/portfolio/insights/underwater

    public record UnderwaterPoint(LocalDate date, double drawdown, long drawdownAmount) {}

    public record EpisodeDto(
            LocalDate peak, LocalDate trough, LocalDate recovered, double depth, long daysToTrough, Long daysToRecover) {}

    public record CurrentDrawdown(double drawdown, LocalDate peak, long days, double deepestPast, boolean deeperThanPast) {}

    public record UnderwaterResponse(List<UnderwaterPoint> series, List<EpisodeDto> episodes, CurrentDrawdown current) {}

    // 7. GET /api/portfolio/insights/monthly

    /** 기록이 없는 달은 returnRate·pnl·netCashFlow 모두 null. */
    public record MonthCell(int month, @JsonProperty("return") Double returnRate, Long pnl, Long netCashFlow) {}

    public record MonthlyTotal(@JsonProperty("return") double returnRate, long pnl) {}

    /** @param months 항상 1–12월 12개 */
    public record MonthlyYear(int year, List<MonthCell> months, MonthlyTotal total) {}

    public record MonthRef(int year, int month, @JsonProperty("return") double returnRate) {}

    public record MonthlyResponse(
            List<MonthlyYear> years, MonthRef best, MonthRef worst, int positiveMonths, int negativeMonths) {}

    // 8. GET /api/portfolio/insights/habits

    public record HoldingSide(int count, double avgDays, double medianDays, double avgReturn) {}

    /** @param verdict HOLD_LOSERS | CUT_LOSSES | BALANCED | INSUFFICIENT */
    public record HoldingHabit(HoldingSide winners, HoldingSide losers, Double ratio, String verdict) {}

    public record AveragingDownItem(
            LocalDate date, InstrumentDto instrument, double priceLocal, double avgCostLocal, double belowAvg, String positionId,
            double positionReturn) {}

    public record AveragingDownDto(
            int buys, double shareOfBuys, int positions, int positionsWon, List<AveragingDownItem> items) {}

    /** @param month YYYY-MM */
    public record ActivityDto(String month, int buys, int sells, double turnover) {}

    public record HabitsResponse(HoldingHabit holding, AveragingDownDto averagingDown, List<ActivityDto> activity) {}

    // 9. GET /api/portfolio/insights/dividends

    public record DividendItem(LocalDate date, InstrumentDto instrument, long amount) {}

    public record DividendMonth(String month, long amount, List<DividendItem> items) {}

    public record DividendYear(int year, long amount) {}

    public record DividendByInstrument(InstrumentDto instrument, long amount, int count) {}

    public record DividendsResponse(
            List<DividendMonth> months, List<DividendYear> byYear, List<DividendByInstrument> byInstrument, long total,
            long trailing12m, double trailingYield) {}

    // 10. GET /api/portfolio/insights/costs

    public record CostTotal(long fee, long tax, long cost) {}

    public record CostMonth(String month, long fee, long tax) {}

    public record CostByInstrument(InstrumentDto instrument, long fee, long tax, int trades) {}

    public record CostsResponse(
            int year, List<Integer> availableYears, CostTotal total, CostTotal allTime, long profitBeforeCost, Double costShare,
            List<CostMonth> months, List<CostByInstrument> byInstrument) {}
}
