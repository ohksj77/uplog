package com.uplog.calc.portfolio;

import static com.uplog.calc.portfolio.Scenario.entry;
import static com.uplog.calc.portfolio.Scenario.missing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MonthlyReturnsTest {

    @Test
    void 월_수익률은_그달_일별_수익률의_곱이고_손익은_하루_손익의_합이다() {
        // 평가금 99,899 → 104,999 → 110,686 → 116,186, 첫날 10만 원 입금
        MonthlyReturns.Result r = MonthlyReturns.of(AccountLog.of(Scenario.history(), Set.of()));

        assertThat(r.years()).singleElement().satisfies(y -> {
            assertThat(y.year()).isEqualTo(2026);
            assertThat(y.months()).hasSize(12).extracting(MonthlyReturns.Month::month)
                    .startsWith(YearMonth.of(2026, 1)).endsWith(YearMonth.of(2026, 12));
            MonthlyReturns.Month oct = y.months().get(9);
            assertThat(oct.days()).isEqualTo(4);
            assertThat(oct.pnl()).isEqualTo(16_186); // −101 + 5,100 + 5,687 + 5,500
            assertThat(oct.netCashFlow()).isEqualTo(100_000);
            assertThat(oct.returnRate()).isCloseTo(116_186.0 / 99_899 - 1, within(1e-12));
            assertThat(y.months().stream().filter(MonthlyReturns.Month::recorded)).hasSize(1);
            assertThat(y.pnl()).isEqualTo(16_186);
            assertThat(y.returnRate()).isCloseTo(oct.returnRate(), within(1e-15));
        });
        assertThat(r.best()).hasValueSatisfying(m -> assertThat(m.month()).isEqualTo(YearMonth.of(2026, 10)));
        assertThat(r.positiveMonths()).isEqualTo(1);
        assertThat(r.negativeMonths()).isZero();
    }

    @Test
    void 수집_실패일의_손익은_다음_기록일의_달로_가고_합계는_같다() {
        MonthlyReturns.Month full = MonthlyReturns.of(AccountLog.of(Scenario.history(), Set.of())).years().getFirst().months().get(9);
        MonthlyReturns.Month gap = MonthlyReturns.of(AccountLog.of(Scenario.history(), Set.of(Scenario.TUE))).years().getFirst().months().get(9);

        assertThat(gap.days()).isEqualTo(3);
        assertThat(gap.pnl()).isEqualTo(full.pnl());
        assertThat(gap.returnRate()).isCloseTo(full.returnRate(), within(1e-12));
    }

    @Test
    void 기록이_없는_달은_비우고_해가_바뀌어도_12칸이다() {
        List<AccountLog.Entry> log = List.of(
                entry(LocalDate.of(2025, 12, 31), 1_000, 1_000, 0, 0.0),
                entry(LocalDate.of(2026, 1, 2), 1_100, 0, 100, 0.1),
                entry(LocalDate.of(2026, 1, 5), 550, 0, -550, -0.5),
                missing(LocalDate.of(2026, 2, 27)), // 2월은 수집 실패일뿐 → 기록 없는 달
                entry(LocalDate.of(2026, 3, 2), 561, 0, 11, 0.02));

        MonthlyReturns.Result r = MonthlyReturns.of(log);

        assertThat(r.years()).extracting(MonthlyReturns.Year::year).containsExactly(2025, 2026);
        MonthlyReturns.Year y2025 = r.years().getFirst();
        assertThat(y2025.months()).filteredOn(MonthlyReturns.Month::recorded).singleElement()
                .satisfies(m -> {
                    assertThat(m.month()).isEqualTo(YearMonth.of(2025, 12));
                    assertThat(m.returnRate()).isZero();
                });
        MonthlyReturns.Year y2026 = r.years().getLast();
        assertThat(y2026.months()).extracting(MonthlyReturns.Month::recorded)
                .containsExactly(true, false, true, false, false, false, false, false, false, false, false, false);
        assertThat(y2026.months().get(0).returnRate()).isCloseTo(1.1 * 0.5 - 1, within(1e-12));
        assertThat(y2026.months().get(0).pnl()).isEqualTo(-450);
        assertThat(y2026.months().get(1).pnl()).isZero();
        assertThat(y2026.pnl()).isEqualTo(-439);
        assertThat(y2026.returnRate()).isCloseTo(1.1 * 0.5 * 1.02 - 1, within(1e-12));

        // 0%인 달(2025-12)은 양·음 어느 쪽에도 세지 않는다
        assertThat(r.positiveMonths()).isEqualTo(1);
        assertThat(r.negativeMonths()).isEqualTo(1);
        assertThat(r.best()).hasValueSatisfying(m -> assertThat(m.month()).isEqualTo(YearMonth.of(2026, 3)));
        assertThat(r.worst()).hasValueSatisfying(m -> assertThat(m.month()).isEqualTo(YearMonth.of(2026, 1)));
    }
}
