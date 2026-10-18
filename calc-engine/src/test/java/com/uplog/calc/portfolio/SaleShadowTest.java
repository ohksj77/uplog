package com.uplog.calc.portfolio;

import static com.uplog.calc.portfolio.Scenario.MON;
import static com.uplog.calc.portfolio.Scenario.sell;
import static com.uplog.calc.portfolio.Scenario.series;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.uplog.calc.PriceSeries;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SaleShadowTest {

    // 매도일(월) 100, 5거래일 뒤 95, 마지막 날(6거래일 뒤) 97
    private final PriceSeries prices = series("100", "101", "99", "98", "96", "95", "97");
    private final LocalDate last = Scenario.weekdays(7).getLast();

    private RealizedSale sale(long qty, String price, String fx) {
        return new RealizedSale(1, sell(MON, "X", qty, price, fx, "0", "0"), MON, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO);
    }

    @Test
    void 매도_N거래일_뒤_종가가_매도가_이하면_판_게_나았다() {
        SaleShadow.Judged j = SaleShadow.judge(sale(10, "100", "1"), prices, null, last, 5);

        assertThat(j.verdict()).isEqualTo(SaleShadow.Verdict.GOOD);
        assertThat(j.afterEffect()).isEqualTo(50); // −10 × (95 − 100)
        assertThat(j.changeAfter().get(5)).hasValueSatisfying(v -> assertThat(v).isCloseTo(-0.05, within(1e-12)));
        assertThat(j.changeAfter().get(20)).isEqualTo(Optional.empty());
        assertThat(j.priceNowLocal()).isEqualByComparingTo("97");
        assertThat(j.changeSinceSale()).isCloseTo(-0.03, within(1e-12));
    }

    @Test
    void 매도가보다_오르면_BAD이고_놓친_이익은_그날_환율로_원화_환산한다() {
        PriceSeries fx = series("1000", "1000", "1000", "1000", "1000", "1200", "1200");
        SaleShadow.Judged j = SaleShadow.judge(sale(2, "90", "1000"), prices, fx, last, 5);

        assertThat(j.verdict()).isEqualTo(SaleShadow.Verdict.BAD);
        assertThat(j.afterEffect()).isEqualTo(-12_000); // −2 × (95 − 90) × 1,200
    }

    @Test
    void 아직_horizon이_안_지났으면_PENDING이고_효과는_0이다() {
        SaleShadow.Judged j = SaleShadow.judge(sale(10, "100", "1"), prices, null, last, 20);
        assertThat(j.verdict()).isEqualTo(SaleShadow.Verdict.PENDING);
        assertThat(j.afterEffect()).isZero();
        assertThatThrownBy(() -> SaleShadow.judge(sale(1, "100", "1"), prices, null, last, 10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 요약은_채점된_매도만_승률에_넣는다() {
        List<SaleShadow.Judged> judged = List.of(
                SaleShadow.judge(sale(10, "100", "1"), prices, null, last, 5),
                SaleShadow.judge(sale(10, "90", "1"), prices, null, last, 5),
                SaleShadow.judge(sale(10, "100", "1"), prices, null, last, 20));
        SaleShadow.Summary s = SaleShadow.summarize(judged.subList(0, 2));
        assertThat(s.sales()).isEqualTo(2);
        assertThat(s.good()).isEqualTo(1);
        assertThat(s.winRate()).isEqualTo(0.5);
        assertThat(s.avoidedLoss()).isEqualTo(50);
        assertThat(s.missedGain()).isEqualTo(50); // 10 × (95 − 90)
        assertThat(SaleShadow.summarize(judged).judged()).isEqualTo(2);
    }
}
