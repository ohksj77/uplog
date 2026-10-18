package com.uplog.calc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

final class Fixtures {

    static final LocalDate MON = LocalDate.of(2026, 10, 12);
    static final LocalDate TUE = MON.plusDays(1);
    static final LocalDate WED = MON.plusDays(2);

    private Fixtures() {}

    static BigDecimal won(long amount) {
        return BigDecimal.valueOf(amount);
    }

    static DailyValuation valuation(LocalDate date, long value, long netCashFlow) {
        return new DailyValuation(date, won(value), won(netCashFlow));
    }

    /** 2026-10-12(월)부터 평일마다 종가를 하나씩 배정한다. */
    static PriceSeries weekdayPrices(long... closes) {
        Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
        LocalDate d = MON;
        for (long close : closes) {
            while (d.getDayOfWeek().getValue() > 5) {
                d = d.plusDays(1);
            }
            map.put(d, won(close));
            d = d.plusDays(1);
        }
        return new PriceSeries(map);
    }
}
