package com.uplog.core;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/** 주말과 지정한 휴장일을 제외한 날을 거래일로 본다. 휴장일 목록은 market-data에서 채운다. */
public final class WeekdayTradingCalendar implements TradingCalendar {

    private final Set<LocalDate> holidays;

    public WeekdayTradingCalendar(Set<LocalDate> holidays) {
        this.holidays = Set.copyOf(holidays);
    }

    public static WeekdayTradingCalendar weekdaysOnly() {
        return new WeekdayTradingCalendar(Set.of());
    }

    @Override
    public boolean isTradingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !holidays.contains(date);
    }
}
