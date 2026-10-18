package com.uplog.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WeekdayTradingCalendarTest {

    // 2026-10-09(금) 한글날
    private final TradingCalendar calendar = new WeekdayTradingCalendar(Set.of(LocalDate.of(2026, 10, 9)));

    @Test
    void 주말과_휴장일은_거래일이_아니다() {
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 10, 8))).isTrue();
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 10, 9))).isFalse();
        assertThat(calendar.isTradingDay(LocalDate.of(2026, 10, 10))).isFalse();
    }

    @Test
    void 휴장일이면_다음_거래일로_민다() {
        assertThat(calendar.onOrAfter(LocalDate.of(2026, 10, 9))).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(calendar.previous(LocalDate.of(2026, 10, 12))).isEqualTo(LocalDate.of(2026, 10, 8));
    }

    @Test
    void 구간의_거래일만_나열한다() {
        assertThat(calendar.tradingDays(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 13)))
                .containsExactly(LocalDate.of(2026, 10, 8), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13));
    }
}
