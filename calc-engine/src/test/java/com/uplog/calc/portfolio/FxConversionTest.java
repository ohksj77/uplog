package com.uplog.calc.portfolio;

import static com.uplog.calc.portfolio.Scenario.series;
import static org.assertj.core.api.Assertions.assertThat;

import com.uplog.calc.PriceSeries;
import org.junit.jupiter.api.Test;

class FxConversionTest {

    @Test
    void 날마다_그날_환율을_곱하고_환율이_없는_날은_직전_환율을_쓴다() {
        PriceSeries spx = series("100", "110", "120");
        PriceSeries fx = series("1000", "1100");

        PriceSeries krw = FxConversion.toKrw(spx, fx);

        assertThat(krw.range(krw.firstDate(), krw.lastDate()).values())
                .extracting(v -> v.intValue())
                .containsExactly(100_000, 121_000, 132_000);
    }
}
