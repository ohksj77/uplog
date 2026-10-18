package com.uplog.calc;

import static com.uplog.calc.Fixtures.MON;
import static com.uplog.calc.Fixtures.TUE;
import static com.uplog.calc.Fixtures.WED;
import static com.uplog.calc.Fixtures.weekdayPrices;
import static com.uplog.calc.Fixtures.won;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class SimulationTest {

    // 100 → 50 → 80: 반토막 뒤 일부 회복
    private final PriceSeries prices = weekdayPrices(100, 50, 80);

    @Test
    void 거치식은_가격_경로를_그대로_따른다() {
        Simulation sim = Simulation.run(new PurchasePlan.LumpSum(won(300)), prices, MON, WED);

        PerformanceSummary s = sim.summary();
        assertThat(s.finalValue()).isEqualByComparingTo("240");
        assertThat(s.invested()).isEqualByComparingTo("300");
        assertThat(s.totalReturn()).isCloseTo(-0.2, within(1e-12));
        assertThat(s.twr()).isCloseTo(-0.2, within(1e-12));

        PainMetrics pain = sim.pain();
        assertThat(pain.maxDrawdown().rate()).isCloseTo(-0.5, within(1e-12));
        assertThat(pain.maxDrawdown().peak()).isEqualTo(MON);
        assertThat(pain.maxDrawdown().trough()).isEqualTo(TUE);
        assertThat(pain.worstDay().date()).isEqualTo(TUE);
        assertThat(pain.longestUnderwater()).hasValueSatisfying(u -> {
            assertThat(u.start()).isEqualTo(TUE);
            assertThat(u.recovered()).isFalse();
        });
    }

    @Test
    void 적립식은_같은_TWR이라도_하락장에_더_많이_사서_돈을_번다() {
        Simulation sim = Simulation.run(new PurchasePlan.Daily(won(100)), prices, MON, WED);

        // 1 + 2 + 1.25 = 4.25주 × 80 = 340
        PerformanceSummary s = sim.summary();
        assertThat(s.finalValue()).isEqualByComparingTo("340");
        assertThat(s.invested()).isEqualByComparingTo("300");
        assertThat(s.profit()).isEqualByComparingTo("40");
        assertThat(s.totalReturn()).isCloseTo(40.0 / 300, within(1e-12));
        assertThat(s.twr()).isCloseTo(-0.2, within(1e-12));

        assertThat(sim.pain().longestUnderwater()).hasValueSatisfying(u -> {
            assertThat(u.start()).isEqualTo(TUE);
            assertThat(u.end()).isEqualTo(WED);
            assertThat(u.recovered()).isTrue();
            assertThat(u.days()).isEqualTo(1);
        });
    }

    @Test
    void 금액_기준_하락폭은_방식마다_실제로_잃은_돈을_보여준다() {
        Simulation lump = Simulation.run(new PurchasePlan.LumpSum(won(300)), prices, MON, WED);
        Simulation dca = Simulation.run(new PurchasePlan.Daily(won(100)), prices, MON, WED);

        // 수익률 기준은 둘 다 −50%
        assertThat(lump.pain().maxDrawdown().rate()).isCloseTo(dca.pain().maxDrawdown().rate(), within(1e-12));

        // 거치식: 평가손익 0 → −150, 하루 손익도 −150
        assertThat(lump.pain().maxProfitDrawdown().amount()).isEqualByComparingTo("-150");
        assertThat(lump.pain().maxProfitDrawdown().trough()).isEqualTo(TUE);
        assertThat(lump.pain().worstDayAmount().pnl()).isEqualByComparingTo("-150");

        // 적립식: 1주만 들고 있을 때 반토막이라 −50, 이후 평가손익 +40으로 회복
        assertThat(dca.pain().maxProfitDrawdown().amount()).isEqualByComparingTo("-50");
        assertThat(dca.pain().worstDayAmount().date()).isEqualTo(TUE);
        assertThat(dca.pain().worstDayAmount().pnl()).isEqualByComparingTo("-50");
    }

    @Test
    void 로그는_거래일마다_한_칸씩_만든다() {
        Simulation sim = Simulation.run(new PurchasePlan.Daily(won(100)), prices, MON.minusDays(2), WED);

        assertThat(sim.points()).extracting(ReplayPoint::date).containsExactly(MON, TUE, WED);
        assertThat(sim.returns()).hasSameSizeAs(sim.points());
    }

    @Test
    void 기간_안에_매수가_없으면_거부한다() {
        assertThatThrownBy(() -> Simulation.run(new PurchasePlan.LumpSum(won(100)), prices, WED.plusDays(1), WED.plusDays(3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 매수한_날의_미세한_계산_오차는_원금_아래로_세지_않는다() {
        // 100원을 3으로 나눠 사면 수량 × 종가가 원금보다 아주 조금 작게 나온다. 이후 오르기만 하면 원금 아래인 날은 없다.
        PriceSeries rising = weekdayPrices(3, 4, 5);

        Simulation sim = Simulation.run(new PurchasePlan.LumpSum(won(100)), rising, MON, WED);

        assertThat(sim.pain().longestUnderwater()).isEmpty();
    }
}
