package com.uplog.calc.portfolio;

import static com.uplog.calc.portfolio.Scenario.MON;
import static com.uplog.calc.portfolio.Scenario.THU;
import static com.uplog.calc.portfolio.Scenario.TUE;
import static com.uplog.calc.portfolio.Scenario.WED;
import static com.uplog.calc.portfolio.Scenario.buy;
import static com.uplog.calc.portfolio.Scenario.deposit;
import static com.uplog.calc.portfolio.Scenario.sell;
import static com.uplog.calc.portfolio.Scenario.series;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.uplog.core.CurrencyCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortfolioReplayTest {

    private final PortfolioHistory history = Scenario.history();

    @Test
    void 현금과_종목_평가금으로_하루_평가금과_손익을_낸다() {
        assertThat(history.days()).extracting(PortfolioDay::date).containsExactly(MON, TUE, WED, THU);
        assertThat(history.days()).extracting(d -> d.value().intValue()).containsExactly(99_899, 104_999, 110_686, 116_186);
        assertThat(history.days()).extracting(d -> d.pnl().intValue()).containsExactly(-101, 5_100, 5_687, 5_500);
        assertThat(history.days()).extracting(d -> d.cash().intValue()).containsExactly(48_899, 48_899, 50_186, 50_186);

        PortfolioDay mon = history.first();
        assertThat(mon.netCashFlow()).isEqualByComparingTo("100000");
        assertThat(mon.dailyReturn()).isZero(); // 전날 평가금이 0
        assertThat(mon.fees()).isEqualByComparingTo("101"); // 1원 + 0.1달러 × 1000
        assertThat(history.days().get(1).dailyReturn()).isCloseTo(5_100.0 / 99_899, within(1e-12));
        assertThat(history.last().twrIndex())
                .isCloseTo((1 + 5_100.0 / 99_899) * (1 + 5_687.0 / 104_999) * (1 + 5_500.0 / 110_686), within(1e-12));
        assertThat(history.last().profit()).isEqualByComparingTo("16186");
    }

    @Test
    void 종목별_손익은_환율_변동을_포함하고_배당_수수료_세금과_합치면_하루_손익이다() {
        PortfolioDay wed = history.days().get(2);
        InstrumentDay kr = wed.instruments().get("KR1");
        InstrumentDay us = wed.instruments().get("US1");

        assertThat(kr.contribution()).isEqualByComparingTo("-110"); // 0 − 1,100 + 990
        assertThat(us.contribution()).isEqualByComparingTo("5500"); // 5 × 11 × (1,100 − 1,000)
        assertThat(us.localPnl()).isEqualByComparingTo("0");
        assertThat(kr.contribution().add(us.contribution()).add(wed.dividends()).subtract(wed.fees()).subtract(wed.taxes()))
                .isEqualByComparingTo(wed.pnl());
    }

    @Test
    void 이동평균_원가와_매수_금액_가중_평균_환율을_쌓고_실현손익을_낸다() {
        Position us = history.last().instruments().get("US1").position();
        assertThat(us.avgCostLocal()).isEqualByComparingTo("10");
        assertThat(us.avgFxRate()).isEqualByComparingTo("1000");
        assertThat(us.costKrw()).isEqualByComparingTo("50000");
        assertThat(history.last().instruments()).doesNotContainKey("KR1"); // 다 판 다음 날부터는 빠진다

        assertThat(history.sales()).singleElement().satisfies(s -> {
            assertThat(s.day()).isEqualTo(WED);
            assertThat(s.realizedProfit()).isEqualByComparingTo("-13"); // 990 − 1,000 − 3
            assertThat(s.avgCostLocal()).isEqualByComparingTo("100");
        });
    }

    @Test
    void 매수_환율이_다르면_평균_환율은_매수_금액으로_가중한다() {
        Position p = Position.EMPTY.buy(BigDecimal.ONE, new BigDecimal("100"), new BigDecimal("100000"))
                .buy(BigDecimal.ONE, new BigDecimal("300"), new BigDecimal("360000"));
        assertThat(p.avgFxRate()).isEqualByComparingTo("1150"); // (100×1000 + 300×1200) / 400
        assertThat(p.sell(BigDecimal.ONE).avgFxRate()).isEqualByComparingTo("1150");
        assertThat(p.sell(BigDecimal.TWO)).isEqualTo(Position.EMPTY);
    }

    @Test
    void 휴장일_사건은_다음_거래일로_옮기고_보유보다_많이_팔면_거부한다() {
        PortfolioReplay.Input weekendDeposit = new PortfolioReplay.Input(
                List.of(MON, TUE), Map.of("KR1", CurrencyCode.KRW), Map.of("KR1", series("100", "100")), null,
                List.of(), List.of(deposit(MON.minusDays(1), 1_000)), List.of());
        assertThat(PortfolioReplay.replay(weekendDeposit).first().netCashFlow()).isEqualByComparingTo("1000");

        PortfolioReplay.Input oversell = new PortfolioReplay.Input(
                List.of(MON, TUE), Map.of("KR1", CurrencyCode.KRW), Map.of("KR1", series("100", "100")), null,
                List.of(buy(MON, "KR1", 1, "100", "1", "0", "0"), sell(TUE, "KR1", 2, "100", "1", "0", "0")),
                List.of(deposit(MON, 1_000)), List.of());
        assertThatThrownBy(() -> PortfolioReplay.replay(oversell)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 달러_종목을_들고_있는데_환율이_없으면_거부한다() {
        PortfolioReplay.Input noFx = new PortfolioReplay.Input(
                List.of(MON), Map.of("US1", CurrencyCode.USD), Map.of("US1", series("10")), null,
                List.of(buy(MON, "US1", 1, "10", "1000", "0", "0")), List.of(deposit(MON, 100_000)), List.of());
        assertThatThrownBy(() -> PortfolioReplay.replay(noFx)).hasMessageContaining("US1");
    }

    @Test
    void 휴장일에는_직전_종가로_평가한다() {
        PortfolioReplay.Input input = new PortfolioReplay.Input(
                List.of(MON, TUE), Map.of("KR1", CurrencyCode.KRW), Map.of("KR1", series("100")), null,
                List.of(buy(MON, "KR1", 1, "100", "1", "0", "0")), List.of(deposit(MON, 1_000)), List.of());
        PortfolioDay tue = PortfolioReplay.replay(input).last();
        assertThat(tue.date()).isEqualTo(TUE);
        assertThat(tue.value()).isEqualByComparingTo("1000");
        assertThat(tue.pnl()).isEqualByComparingTo("0");
    }
}
