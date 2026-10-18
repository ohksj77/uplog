package com.uplog.simulation;

import com.uplog.calc.PriceSeries;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

final class Prices {

    static final LocalDate MON = LocalDate.of(2026, 10, 12);

    private Prices() {}

    /** start부터 평일마다 종가를 하나씩 배정한다. */
    static PriceSeries weekdays(LocalDate start, double... closes) {
        Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
        LocalDate d = start;
        for (double close : closes) {
            while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                d = d.plusDays(1);
            }
            map.put(d, BigDecimal.valueOf(close));
            d = d.plusDays(1);
        }
        return new PriceSeries(map);
    }

    /** start부터 평일 n일 동안 같은 종가. */
    static PriceSeries flat(LocalDate start, int n, double close) {
        double[] closes = new double[n];
        java.util.Arrays.fill(closes, close);
        return weekdays(start, closes);
    }
}
