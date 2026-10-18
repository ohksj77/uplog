package com.uplog.simulation;

import static com.uplog.simulation.Prices.MON;
import static org.assertj.core.api.Assertions.assertThat;

import com.uplog.calc.PriceSeries;
import com.uplog.calc.PurchasePlan;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import org.junit.jupiter.api.Test;

class RecurringScheduleTest {

    @Test
    void 주기별로_calc_engine의_PurchasePlan으로_바꾼다() {
        BigDecimal amount = BigDecimal.valueOf(10_000);

        assertThat(RecurringSchedule.daily().plan(amount)).isEqualTo(new PurchasePlan.Daily(amount));
        assertThat(RecurringSchedule.weekly(DayOfWeek.WEDNESDAY).plan(amount))
                .isEqualTo(new PurchasePlan.Weekly(amount, DayOfWeek.WEDNESDAY));
        assertThat(RecurringSchedule.monthly(31).plan(amount)).isEqualTo(new PurchasePlan.Monthly(amount, 31));
    }

    @Test
    void 회차_수는_구간_안에서_실제로_사는_날의_수다() {
        PriceSeries prices = Prices.flat(MON, 15, 100); // 3주, 10/12(월)–10/30(금)
        var days = prices.tradingDays(prices.firstDate(), prices.lastDate());

        assertThat(RecurringSchedule.daily().purchaseCount(days)).isEqualTo(15);
        assertThat(RecurringSchedule.weekly(DayOfWeek.FRIDAY).purchaseCount(days)).isEqualTo(3);
        // 10/12 시작이라 10월 1일은 지났고, 11월 1일은 구간 밖
        assertThat(RecurringSchedule.monthly(1).purchaseCount(days)).isZero();
        assertThat(RecurringSchedule.monthly(20).purchaseCount(days)).isEqualTo(1);
    }
}
