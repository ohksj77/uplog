package com.uplog.calc;

import static com.uplog.calc.Fixtures.MON;
import static com.uplog.calc.Fixtures.TUE;
import static com.uplog.calc.Fixtures.WED;
import static com.uplog.calc.Fixtures.valuation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class TwrTest {

    @Test
    void 입출금이_없으면_일별_수익률을_곱해_누적한다() {
        List<DailyReturn> returns = Twr.compute(List.of(
                valuation(MON, 100, 100),
                valuation(TUE, 110, 0),
                valuation(WED, 99, 0)));

        assertThat(returns).extracting(DailyReturn::dailyReturn)
                .containsExactly(0.0, 0.1, -0.1);
        assertThat(returns.getLast().twrIndex()).isCloseTo(0.99, within(1e-12));
    }

    @Test
    void 입금과_출금은_수익으로_치지_않는다() {
        List<DailyReturn> returns = Twr.compute(List.of(
                valuation(MON, 100, 100),
                valuation(TUE, 210, 100), // 100 입금 + 10 수익
                valuation(WED, 181, -50))); // 50 출금 + 21 수익

        assertThat(returns.get(1).dailyReturn()).isCloseTo(0.1, within(1e-12));
        assertThat(returns.get(2).dailyReturn()).isCloseTo(0.1, within(1e-12));
        assertThat(returns.getLast().cumulativeReturn()).isCloseTo(0.21, within(1e-12));
    }

    @Test
    void 전날_평가금액이_0이면_수익률은_0이다() {
        List<DailyReturn> returns = Twr.compute(List.of(
                valuation(MON, 100, 100),
                valuation(TUE, 0, -100),
                valuation(WED, 50, 50)));

        assertThat(returns).extracting(DailyReturn::twrIndex).containsExactly(1.0, 1.0, 1.0);
    }

    @Test
    void 날짜가_오름차순이_아니면_거부한다() {
        assertThatThrownBy(() -> Twr.compute(List.of(valuation(TUE, 100, 100), valuation(MON, 100, 0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 구간_수익률과_연환산() {
        List<DailyReturn> returns = Twr.compute(List.of(
                valuation(MON, 100, 100),
                valuation(TUE, 110, 0),
                valuation(WED, 121, 0)));

        assertThat(Twr.between(returns.get(1), returns.get(2))).isCloseTo(0.1, within(1e-12));
        assertThat(Twr.annualize(1.21, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 12, 31)))
                .isCloseTo(0.1, within(1e-12));
        assertThat(Twr.annualize(1.5, MON, MON)).isZero();
    }
}
