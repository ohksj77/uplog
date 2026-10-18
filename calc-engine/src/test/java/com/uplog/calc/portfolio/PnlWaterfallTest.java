package com.uplog.calc.portfolio;

import static com.uplog.calc.portfolio.Scenario.MON;
import static com.uplog.calc.portfolio.Scenario.TUE;
import static com.uplog.calc.portfolio.Scenario.WED;
import static com.uplog.calc.portfolio.Scenario.buy;
import static com.uplog.calc.portfolio.Scenario.deposit;
import static com.uplog.calc.portfolio.Scenario.series;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.uplog.calc.portfolio.PnlWaterfall.Kind;
import com.uplog.core.CurrencyCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PnlWaterfallTest {

    @Test
    void 기간_손익을_종목별_주가_효과_배당_수수료_세금_환율로_정확히_나눈다() {
        PnlWaterfall.Result r = PnlWaterfall.of(Scenario.history(), 0, 3);

        assertThat(r.from()).isEqualTo(MON);
        assertThat(r.startValue()).isEqualTo(99_899);
        assertThat(r.endValue()).isEqualTo(116_186);
        assertThat(r.netCashFlow()).isZero();
        assertThat(r.total()).isEqualTo(16_287);
        // US1: 현지 손익 10달러 × 시작일 환율 1,000 = 10,000, 환율 효과 5 × 12 × 100 = 6,000
        assertThat(r.items()).extracting(PnlWaterfall.Item::kind, PnlWaterfall.Item::instrument, PnlWaterfall.Item::amount)
                .containsExactly(
                        tuple(Kind.INSTRUMENT, "US1", 10_000L),
                        tuple(Kind.INSTRUMENT, "KR1", -10L),
                        tuple(Kind.DIVIDEND, null, 300L),
                        tuple(Kind.FEE, null, -1L),
                        tuple(Kind.TAX, null, -2L),
                        tuple(Kind.FX, null, 6_000L));
        assertThat(r.items().stream().mapToLong(PnlWaterfall.Item::amount).sum()).isEqualTo(r.total());
    }

    @Test
    void 기간_중에_연_포지션은_매수_환율을_기준으로_주가_효과를_잰다() {
        // 월 환율 900, 화 1,000에 5주 @11 매수, 수 12달러·1,100원
        PortfolioReplay.Input input = new PortfolioReplay.Input(
                List.of(MON, TUE, WED), Map.of("US1", CurrencyCode.USD), Map.of("US1", series("10", "11", "12")),
                series("900", "1000", "1100"),
                List.of(buy(TUE, "US1", 5, "11", "1000", "0", "0")), List.of(deposit(MON, 100_000)), List.of());

        PnlWaterfall.Result r = PnlWaterfall.of(PortfolioReplay.replay(input), 0, 2);

        assertThat(r.items()).extracting(PnlWaterfall.Item::kind, PnlWaterfall.Item::amount)
                .containsExactly(
                        tuple(Kind.INSTRUMENT, 5_000L), // 5 × (12 − 11) × 1,000
                        tuple(Kind.DIVIDEND, 0L),
                        tuple(Kind.FEE, 0L),
                        tuple(Kind.TAX, 0L),
                        tuple(Kind.FX, 6_000L)); // 5 × 12 × (1,100 − 1,000)
        assertThat(r.total()).isEqualTo(11_000);
        assertThat(r.to()).isEqualTo(WED);
    }

    @Test
    void 반올림_차이는_기타_한_줄로_맞춘다() {
        PortfolioReplay.Input input = new PortfolioReplay.Input(
                List.of(MON, TUE), Map.of("KR1", CurrencyCode.KRW, "KR2", CurrencyCode.KRW),
                Map.of("KR1", series("100", "100.5"), "KR2", series("100", "100.5")), null,
                List.of(buy(MON, "KR1", 1, "100", "1", "0", "0"), buy(MON, "KR2", 1, "100", "1", "0", "0")),
                List.of(deposit(MON, 1_000)), List.of());

        PnlWaterfall.Result r = PnlWaterfall.of(PortfolioReplay.replay(input), 0, 1);

        assertThat(r.total()).isEqualTo(1); // 1,001 − 1,000
        assertThat(r.items()).extracting(PnlWaterfall.Item::amount).containsExactly(1L, 1L, 0L, 0L, 0L, 0L, -1L);
        assertThat(r.items().getLast().kind()).isEqualTo(Kind.OTHER);
    }
}
