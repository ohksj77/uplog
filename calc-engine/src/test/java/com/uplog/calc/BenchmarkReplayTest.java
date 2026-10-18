package com.uplog.calc;

import static com.uplog.calc.Fixtures.MON;
import static com.uplog.calc.Fixtures.TUE;
import static com.uplog.calc.Fixtures.WED;
import static com.uplog.calc.Fixtures.weekdayPrices;
import static com.uplog.calc.Fixtures.won;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BenchmarkReplayTest {

    private final PriceSeries prices = weekdayPrices(100, 110, 121);

    @Test
    void 휴장일_현금흐름은_다음_거래일_종가로_체결한다() {
        List<ReplayPoint> points = BenchmarkReplay.replay(
                List.of(new CashFlow(MON.minusDays(2), won(1_000)), new CashFlow(TUE, won(1_100))), prices, WED);

        assertThat(points).extracting(ReplayPoint::date).containsExactly(MON, TUE, WED);
        assertThat(points.getFirst().units()).isEqualByComparingTo("10");
        ReplayPoint last = points.getLast();
        assertThat(last.units()).isEqualByComparingTo("20");
        assertThat(last.value()).isEqualByComparingTo("2420");
        assertThat(last.invested()).isEqualByComparingTo("2100");
    }

    @Test
    void 평가금액보다_큰_출금은_전량_매도로_처리한다() {
        PriceSeries falling = weekdayPrices(100, 50);

        List<ReplayPoint> points = BenchmarkReplay.replay(
                List.of(new CashFlow(MON, won(1_000)), new CashFlow(TUE, won(-1_000))), falling, TUE);

        ReplayPoint last = points.getLast();
        assertThat(last.units()).isEqualByComparingTo("0");
        assertThat(last.netCashFlow()).isEqualByComparingTo("-500");
        assertThat(last.invested()).isEqualByComparingTo("500");
    }

    @Test
    void 평가_종료일_이후의_현금흐름은_반영하지_않는다() {
        List<ReplayPoint> points = BenchmarkReplay.replay(
                List.of(new CashFlow(MON, won(1_000)), new CashFlow(WED, won(5_000))), prices, TUE);

        assertThat(points).hasSize(2);
        assertThat(points.getLast().invested()).isEqualByComparingTo("1000");
    }

    @Test
    void 체결된_현금흐름이_없으면_빈_결과() {
        assertThat(BenchmarkReplay.replay(List.of(new CashFlow(WED.plusDays(1), won(1_000))), prices, WED))
                .isEmpty();
    }
}
