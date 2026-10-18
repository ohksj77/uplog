package com.uplog.calc.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UnderwaterTest {

    private final List<LocalDate> days = Scenario.weekdays(7);

    /** TWR 지수 1.0 → 1.1 → 0.99 → 1.045 → 1.1 → 1.21 → 0.968, 순입금 1,000 고정. */
    private List<AccountLog.Entry> entries(double[] index, long[] value) {
        List<AccountLog.Entry> entries = new ArrayList<>();
        for (int i = 0; i < index.length; i++) {
            entries.add(new AccountLog.Entry(days.get(i), false, BigDecimal.valueOf(value[i]), BigDecimal.ZERO,
                    BigDecimal.ZERO, 0.0, index[i], BigDecimal.valueOf(1_000), List.of(), BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), List.of()));
        }
        return entries;
    }

    @Test
    void 고점_아래로_내려갔다가_되찾은_구간과_진행_중인_구간을_깊은_순으로_낸다() {
        Underwater.Result r = Underwater.of(entries(
                new double[] {1.0, 1.1, 0.99, 1.045, 1.1, 1.21, 0.968},
                new long[] {1_000, 1_100, 990, 1_045, 1_100, 1_210, 968}));

        assertThat(r.series()).extracting(Underwater.Point::drawdown)
                .satisfies(dd -> {
                    assertThat(dd.get(1)).isZero();
                    assertThat(dd.get(2)).isCloseTo(-0.1, within(1e-12));
                    assertThat(dd.get(4)).isZero();
                    assertThat(dd.get(6)).isCloseTo(-0.2, within(1e-12));
                });
        assertThat(r.series().get(6).drawdownAmount()).isEqualByComparingTo("-242"); // (968 − 1,000) − (1,210 − 1,000)

        assertThat(r.episodes()).hasSize(2);
        Underwater.Episode now = r.episodes().getFirst();
        assertThat(now.peak()).isEqualTo(days.get(5));
        assertThat(now.recovered()).isEmpty();
        assertThat(now.daysToRecover()).isEmpty();
        Underwater.Episode past = r.episodes().getLast();
        assertThat(past.peak()).isEqualTo(days.get(1));
        assertThat(past.trough()).isEqualTo(days.get(2));
        assertThat(past.recovered()).isEqualTo(Optional.of(days.get(4)));
        assertThat(past.daysToTrough()).isEqualTo(1);
        assertThat(past.daysToRecover()).hasValue(2L); // 수(바닥) → 금(회복)

        assertThat(r.current().drawdown()).isCloseTo(-0.2, within(1e-12));
        assertThat(r.current().peak()).isEqualTo(days.get(5));
        assertThat(r.current().days()).isEqualTo(1); // 월(고점) → 화, 달력일
        assertThat(r.current().deepestPast()).isCloseTo(-0.1, within(1e-12));
        assertThat(r.current().deeperThanPast()).isTrue();
    }

    @Test
    void 지금_고점이면_현재_구간은_0이고_그날이_고점이다() {
        Underwater.Result r = Underwater.of(entries(new double[] {1.0, 0.9, 1.2}, new long[] {1_000, 900, 1_200}));

        assertThat(r.current().drawdown()).isZero();
        assertThat(r.current().peak()).isEqualTo(days.get(2));
        assertThat(r.current().days()).isZero();
        assertThat(r.current().deepestPast()).isCloseTo(-0.1, within(1e-12));
        assertThat(r.current().deeperThanPast()).isFalse();
    }
}
