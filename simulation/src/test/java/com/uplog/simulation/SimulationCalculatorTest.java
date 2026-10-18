package com.uplog.simulation;

import static com.uplog.simulation.Prices.MON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.uplog.calc.PriceSeries;
import com.uplog.simulation.api.SimulationResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SimulationCalculatorTest {

    private static SimulationQuery query(RecurringSchedule schedule, AmountType type, long amount) {
        return new SimulationQuery(java.util.List.of("TEST01"), SimulationPeriod.SIX_MONTHS, schedule, type, amount);
    }

    @Test
    void 거치식은_적립식의_실제_총_투입금을_첫_거래일에_한_번에_넣는다() {
        // 주마다 종가가 100 → 50 → 80
        PriceSeries prices = Prices.weekdays(MON, 100, 100, 100, 100, 100, 50, 50, 50, 50, 50, 80, 80, 80, 80, 80);
        LocalDate to = prices.lastDate();

        SimulationCalculator.Pair pair = SimulationCalculator.calculate(
                query(RecurringSchedule.weekly(DayOfWeek.MONDAY), AmountType.TOTAL, 100_000), prices, MON, to);

        // 월요일 3번 × 33,333원
        assertThat(pair.recurring().summary().invested()).isEqualTo(99_999);
        assertThat(pair.lumpSum().summary().invested()).isEqualTo(99_999);
        assertThat(pair.lumpSum().days()).hasSize(15).first().satisfies(d -> {
            assertThat(d.date()).isEqualTo(MON);
            assertThat(d.purchased()).isTrue();
            assertThat(d.value()).isEqualTo(99_999);
        });
        assertThat(pair.lumpSum().days()).filteredOn(SimulationResponse.Day::purchased).hasSize(1);
        assertThat(pair.recurring().days()).filteredOn(SimulationResponse.Day::purchased)
                .extracting(SimulationResponse.Day::date)
                .containsExactly(MON, MON.plusWeeks(1), MON.plusWeeks(2));

        // 거치식: 99,999 × 0.8 = 79,999.2 → 79,999
        SimulationResponse.Summary lump = pair.lumpSum().summary();
        assertThat(lump.finalValue()).isEqualTo(79_999);
        assertThat(lump.profit()).isEqualTo(lump.finalValue() - lump.invested());
        assertThat(lump.totalReturn()).isCloseTo(-0.2, within(1e-9));
        assertThat(pair.lumpSum().pain().maxDrawdown().rate()).isCloseTo(-0.5, within(1e-12));
        // 99,999원이 반토막: 평가손익 0 → −49,999.5 → 반올림 −50,000
        assertThat(pair.lumpSum().pain().maxDrawdownAmount().amount()).isEqualTo(-50_000);
        assertThat(pair.lumpSum().pain().worstDayAmount().pnl()).isEqualTo(-50_000);
        assertThat(pair.lumpSum().pain().underwater()).isNotNull()
                .satisfies(u -> assertThat(u.recovered()).isFalse());

        // 적립식: 33,333원씩 100·50·80에 사서 약 1,416.7주 × 80 ≈ 113,333원 → 수익
        assertThat(pair.recurring().summary().finalValue()).isGreaterThan(pair.recurring().summary().invested());
    }

    @Test
    void 원금_아래로_내려간_적이_없으면_underwater는_null이다() {
        PriceSeries prices = Prices.weekdays(MON, 100, 101, 102, 103, 104);

        SimulationCalculator.Pair pair = SimulationCalculator.calculate(
                query(RecurringSchedule.daily(), AmountType.PER_PURCHASE, 10_000), prices, MON, prices.lastDate());

        assertThat(pair.lumpSum().pain().underwater()).isNull();
        assertThat(pair.recurring().pain().underwater()).isNull();
        assertThat(pair.lumpSum().summary().invested()).isEqualTo(50_000);
        assertThat(pair.recurring().days()).allSatisfy(d -> assertThat(d.purchased()).isTrue());
        assertThat(pair.recurring().days().getFirst().close()).isEqualTo(100);
        assertThat(pair.recurring().days().getFirst().dailyReturn()).isZero();
    }

    @Test
    void 적립식_days는_첫_매수일부터_시작한다() {
        PriceSeries prices = Prices.flat(MON, 10, 1_000); // 10/12–10/23

        SimulationCalculator.Pair pair = SimulationCalculator.calculate(
                query(RecurringSchedule.monthly(20), AmountType.PER_PURCHASE, 10_000), prices, MON, prices.lastDate());

        assertThat(pair.recurring().days().getFirst().date()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(pair.recurring().days()).hasSize(4);
        assertThat(pair.lumpSum().days()).hasSize(10);
        assertThat(pair.lumpSum().summary().invested()).isEqualTo(10_000);
    }

    @Test
    void 금액은_원_단위로_반올림한다() {
        assertThat(SimulationCalculator.won(new java.math.BigDecimal("1234.5"))).isEqualTo(1235);
        assertThat(SimulationCalculator.won(new java.math.BigDecimal("1234.4999"))).isEqualTo(1234);
    }

    @Test
    void 연속_기록을_함께_돌려준다() {
        // 100 → 101 → 102 → 103 → 102: 3거래일 연속 상승 후 하락
        PriceSeries prices = Prices.weekdays(MON, 100, 101, 102, 103, 102);

        SimulationCalculator.Pair pair = SimulationCalculator.calculate(
                query(RecurringSchedule.daily(), AmountType.PER_PURCHASE, 10_000), prices, MON, prices.lastDate());

        SimulationResponse.StreakSet lump = pair.lumpSum().streaks();
        assertThat(lump.gain().longest()).isEqualTo(new SimulationResponse.Streak(3, MON.plusDays(1), MON.plusDays(3)));
        assertThat(lump.gain().current()).isNull();
        assertThat(lump.loss().current()).isEqualTo(new SimulationResponse.Streak(1, MON.plusDays(4), MON.plusDays(4)));
        assertThat(lump.purchase()).isNull();

        SimulationResponse.PurchaseStreak purchase = pair.recurring().streaks().purchase();
        assertThat(purchase.unit()).isEqualTo("DAY");
        assertThat(purchase.longest().length()).isEqualTo(5);
        assertThat(purchase.current()).isEqualTo(purchase.longest());
    }
}
