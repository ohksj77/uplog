package com.uplog.calc;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * @param totalReturn 단순 수익률 = 평가손익 / 순투입 원금
 * @param twr 시간가중수익률(입출금 효과 제거)
 * @param annualizedTwr TWR 연환산
 */
public record PerformanceSummary(
        BigDecimal finalValue, BigDecimal invested, BigDecimal profit, double totalReturn, double twr, double annualizedTwr) {

    public static PerformanceSummary of(List<ReplayPoint> points, List<DailyReturn> returns) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("시계열이 비어 있습니다");
        }
        ReplayPoint last = points.getLast();
        BigDecimal profit = last.value().subtract(last.invested());
        double totalReturn = last.invested().signum() > 0
                ? profit.divide(last.invested(), MathContext.DECIMAL64).doubleValue()
                : 0.0;
        double index = returns.getLast().twrIndex();
        return new PerformanceSummary(
                last.value(),
                last.invested(),
                profit,
                totalReturn,
                index - 1.0,
                Twr.annualize(index, returns.getFirst().date(), returns.getLast().date()));
    }
}
