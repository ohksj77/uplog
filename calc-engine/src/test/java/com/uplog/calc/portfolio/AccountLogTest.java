package com.uplog.calc.portfolio;

import static com.uplog.calc.portfolio.Scenario.TUE;
import static com.uplog.calc.portfolio.Scenario.WED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccountLogTest {

    @Test
    void 수집_실패일은_비우고_그날_손익은_다음_기록일에_몰아_넣는다() {
        List<AccountLog.Entry> log = AccountLog.of(Scenario.history(), Set.of(TUE));

        AccountLog.Entry tue = log.get(1);
        assertThat(tue.missing()).isTrue();
        assertThat(tue.value()).isZero();
        assertThat(tue.pnl()).isZero();

        AccountLog.Entry wed = log.get(2);
        assertThat(wed.date()).isEqualTo(WED);
        assertThat(wed.pnl()).isEqualByComparingTo("10787"); // 110,686 − 99,899
        assertThat(wed.dailyReturn()).isCloseTo(10_787.0 / 99_899, within(1e-12));
        assertThat(wed.contributions()).extracting(AccountLog.Contribution::instrument).containsExactly("US1", "KR1");
        assertThat(wed.contributions()).extracting(c -> c.pnl().intValue()).containsExactly(10_500, -10);
        BigDecimal sum = wed.contributions().stream().map(AccountLog.Contribution::pnl).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum.add(wed.dividends()).subtract(wed.fees()).subtract(wed.taxes())).isEqualByComparingTo(wed.pnl());

        // 수집 실패일을 건너뛰어도 TWR 지수는 같다
        assertThat(log.getLast().twrIndex()).isCloseTo(Scenario.history().last().twrIndex(), within(1e-12));
        assertThat(AccountLog.observed(log)).hasSize(3);
    }

    @Test
    void 종목_비중은_그날_평가금_기준이다() {
        AccountLog.Entry thu = AccountLog.of(Scenario.history(), Set.of()).getLast();
        assertThat(thu.contributions()).singleElement().satisfies(c -> {
            assertThat(c.weight()).isCloseTo(66_000.0 / 116_186, within(1e-12));
            assertThat(c.dailyReturn()).isCloseTo(5_500.0 / 60_500, within(1e-12));
        });
    }
}
