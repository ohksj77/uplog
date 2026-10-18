package com.uplog.calc;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * "같은 날 같은 금액을 이 종목에 넣었다면"을 계산한다.
 * 기능 A의 시뮬레이션(가상의 현금흐름)과 기능 B의 벤치마크 비교(실제 현금흐름)가 함께 쓴다.
 */
public final class BenchmarkReplay {

    private BenchmarkReplay() {}

    /**
     * 각 현금흐름을 그날(휴장일이면 다음 거래일) 종가로 매수·매도하고, 첫 체결일부터 {@code until}까지 평가한다.
     * 시세 범위 밖으로 밀린 현금흐름은 아직 체결되지 않은 것으로 보고 반영하지 않는다.
     * 출금이 평가금액보다 크면 전량 매도로 처리한다.
     */
    public static List<ReplayPoint> replay(List<CashFlow> flows, PriceSeries prices, LocalDate until) {
        TreeMap<LocalDate, BigDecimal> flowsByTradingDay = new TreeMap<>();
        for (CashFlow flow : flows) {
            prices.tradingDayOnOrAfter(flow.date())
                    .filter(day -> !day.isAfter(until))
                    .ifPresent(day -> flowsByTradingDay.merge(day, flow.amount(), BigDecimal::add));
        }
        if (flowsByTradingDay.isEmpty()) {
            return List.of();
        }

        List<ReplayPoint> points = new ArrayList<>();
        BigDecimal units = BigDecimal.ZERO;
        BigDecimal invested = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> day : prices.range(flowsByTradingDay.firstKey(), until).entrySet()) {
            BigDecimal close = day.getValue();
            BigDecimal requested = flowsByTradingDay.getOrDefault(day.getKey(), BigDecimal.ZERO);
            BigDecimal executed = requested;
            if (requested.signum() != 0) {
                BigDecimal deltaUnits = requested.divide(close, MathContext.DECIMAL64);
                if (units.add(deltaUnits).signum() < 0) {
                    deltaUnits = units.negate();
                    executed = deltaUnits.multiply(close);
                }
                units = units.add(deltaUnits);
                invested = invested.add(executed);
            }
            points.add(new ReplayPoint(day.getKey(), close, units, units.multiply(close), invested, executed));
        }
        return List.copyOf(points);
    }
}
