package com.uplog.calc;

import java.time.LocalDate;
import java.util.List;

/** 기능 A: "그때 이 방식으로 샀다면"의 결과. 로그는 {@code points}와 {@code returns}를 날짜별로 그린다. */
public record Simulation(
        List<ReplayPoint> points, List<DailyReturn> returns, PerformanceSummary summary, PainMetrics pain) {

    public static Simulation run(PurchasePlan plan, PriceSeries prices, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("기간이 잘못됐습니다: " + from + " ~ " + to);
        }
        List<ReplayPoint> points = BenchmarkReplay.replay(plan.cashFlows(prices.tradingDays(from, to)), prices, to);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("기간 안에 체결된 매수가 없습니다: " + from + " ~ " + to);
        }
        List<DailyReturn> returns = Twr.compute(points.stream().map(ReplayPoint::toValuation).toList());
        return new Simulation(points, returns, PerformanceSummary.of(points, returns), PainMetrics.of(points, returns));
    }
}
