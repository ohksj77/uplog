package com.uplog.calc.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FxDecompositionTest {

    @Test
    void 해외_보유분의_손익을_주가_효과와_환율_효과로_정확히_나눈다() {
        FxDecomposition.Result r = FxDecomposition.of(Scenario.history().last(), Scenario.CURRENCIES);

        // q=5, P0=10, F0=1,000, P1=12, F1=1,100
        assertThat(r.items()).singleElement().satisfies(i -> {
            assertThat(i.instrument()).isEqualTo("US1");
            assertThat(i.avgCostLocal()).isEqualByComparingTo("10");
            assertThat(i.avgFxRate()).isEqualByComparingTo("1000");
            assertThat(i.priceEffect()).isEqualTo(10_000); // 5 × (12 − 10) × 1,000
            assertThat(i.fxEffect()).isEqualTo(6_000); // 5 × 12 × (1,100 − 1,000)
            assertThat(i.profit()).isEqualTo(16_000);
        });
        assertThat(r.total().priceEffect() + r.total().fxEffect()).isEqualTo(r.total().profit());
    }
}
