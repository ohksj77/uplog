package com.uplog.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public interface TradingCalendar {

    boolean isTradingDay(LocalDate date);

    /** date가 거래일이면 그대로, 아니면 다음 거래일. */
    default LocalDate onOrAfter(LocalDate date) {
        LocalDate d = date;
        while (!isTradingDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** date 이전(date 제외)의 가장 가까운 거래일. */
    default LocalDate previous(LocalDate date) {
        LocalDate d = date.minusDays(1);
        while (!isTradingDay(d)) {
            d = d.minusDays(1);
        }
        return d;
    }

    /** [from, to] 구간의 거래일. */
    default List<LocalDate> tradingDays(LocalDate from, LocalDate to) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (isTradingDay(d)) {
                days.add(d);
            }
        }
        return days;
    }
}
