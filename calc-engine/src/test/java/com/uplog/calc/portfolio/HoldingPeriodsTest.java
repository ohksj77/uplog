package com.uplog.calc.portfolio;

import static com.uplog.calc.portfolio.Scenario.MON;
import static com.uplog.calc.portfolio.Scenario.WED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HoldingPeriodsTest {

    @Test
    void 수량이_0에서_양수가_된_날부터_다시_0이_된_날까지가_한_포지션이다() {
        List<HoldingPeriods.Period> periods = HoldingPeriods.of(Scenario.history());

        assertThat(periods).extracting(HoldingPeriods.Period::instrument).containsExactly("KR1", "US1");
        HoldingPeriods.Period kr = periods.getFirst();
        assertThat(kr.open()).isEqualTo(MON);
        assertThat(kr.close()).isEqualTo(Optional.of(WED));
        assertThat(kr.days()).isEqualTo(2);
        assertThat(kr.profit()).isEqualByComparingTo("-14"); // 990 − 1,000 − 수수료 2 − 세금 2
        assertThat(kr.returnRate()).isCloseTo(-0.014, within(1e-12));
        assertThat(kr.bucket()).isEqualTo(HoldingPeriods.Bucket.SHORT);

        HoldingPeriods.Period us = periods.getLast();
        assertThat(us.isOpen()).isTrue();
        assertThat(us.days()).isEqualTo(3);
        assertThat(us.profit()).isEqualByComparingTo("16200"); // 66,000 + 배당 300 − 50,000 − 수수료 100
        assertThat(us.returnRate()).isCloseTo(0.324, within(1e-12));
    }

    @Test
    void 버킷_경계는_7_90_365일이다() {
        assertThat(HoldingPeriods.Bucket.of(0)).isEqualTo(HoldingPeriods.Bucket.SHORT);
        assertThat(HoldingPeriods.Bucket.of(7)).isEqualTo(HoldingPeriods.Bucket.SHORT);
        assertThat(HoldingPeriods.Bucket.of(8)).isEqualTo(HoldingPeriods.Bucket.SWING);
        assertThat(HoldingPeriods.Bucket.of(90)).isEqualTo(HoldingPeriods.Bucket.SWING);
        assertThat(HoldingPeriods.Bucket.of(91)).isEqualTo(HoldingPeriods.Bucket.MID);
        assertThat(HoldingPeriods.Bucket.of(365)).isEqualTo(HoldingPeriods.Bucket.MID);
        assertThat(HoldingPeriods.Bucket.of(366)).isEqualTo(HoldingPeriods.Bucket.LONG);
    }
}
