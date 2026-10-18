package com.uplog.calc.portfolio;

import static com.uplog.calc.portfolio.Scenario.THU;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AllocationHistoryTest {

    private static final Set<String> OVERSEAS = Set.of("US1");

    @Test
    void 주마다_마지막_날을_점으로_쓰고_합이_총평가금과_같다() {
        AllocationHistory.Result r = AllocationHistory.of(Scenario.history(), Set.copyOf(Scenario.DAYS), OVERSEAS, 7);

        // 월–목이 한 주라 점은 목요일 하나. KR1은 수요일에 다 팔아 목요일엔 US1과 현금만 남는다.
        assertThat(r.points()).singleElement().satisfies(p -> {
            assertThat(p.date()).isEqualTo(THU);
            assertThat(p.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(p.total());
            assertThat(p.kr().add(p.us()).add(p.cash())).isEqualByComparingTo(p.total());
            assertThat(p.kr()).isEqualByComparingTo("0");
            assertThat(p.us()).isPositive();
        });
        assertThat(r.groups()).extracting(AllocationHistory.Group::key).containsExactly("US1", AllocationHistory.CASH);
        assertThat(r.peak()).isNotNull().satisfies(pk -> {
            assertThat(pk.key()).isEqualTo("US1");
            assertThat(pk.date()).isEqualTo(THU);
            assertThat(pk.weight()).isBetween(0.0, 1.0);
        });
    }

    @Test
    void 따로_보여_줄_종목_수를_넘으면_그_외로_묶는다() {
        AllocationHistory.Result r = AllocationHistory.of(Scenario.history(), Set.copyOf(Scenario.DAYS), OVERSEAS, 0);

        assertThat(r.groups()).extracting(AllocationHistory.Group::key)
                .containsExactly(AllocationHistory.OTHER, AllocationHistory.CASH);
        AllocationHistory.Point p = r.points().getFirst();
        assertThat(p.values().getFirst()).isEqualByComparingTo(p.us());
    }

    @Test
    void 기록이_없는_날은_점으로_쓰지_않는다() {
        AllocationHistory.Result r = AllocationHistory.of(Scenario.history(), Set.of(Scenario.MON), OVERSEAS, 7);

        assertThat(r.points()).extracting(AllocationHistory.Point::date).containsExactly(Scenario.MON);
    }
}
