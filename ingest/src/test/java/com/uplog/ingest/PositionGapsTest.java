package com.uplog.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.uplog.calc.Trade.Side;
import com.uplog.calc.portfolio.Execution;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PositionGapsTest {

    private static final LocalDate D1 = LocalDate.of(2025, 3, 3);
    private static final LocalDate D2 = LocalDate.of(2025, 3, 4);

    private static Execution trade(LocalDate date, String code, Side side, String qty, String price) {
        return new Execution(date, code, side, new BigDecimal(qty), new BigDecimal(price), BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void 매수_없이_판_수량은_그_매도가로_추정_입고한다() {
        List<PositionGaps.Gap> gaps = PositionGaps.oversells(List.of(trade(D1, "005930", Side.SELL, "1", "70000")));

        assertThat(gaps).singleElement().satisfies(g -> {
            assertThat(g.date()).isEqualTo(D1);
            assertThat(g.symbol()).isEqualTo("005930");
            assertThat(g.quantity()).isEqualByComparingTo("1");
            assertThat(g.amountKrw()).isEqualByComparingTo("70000");
        });
    }

    @Test
    void 보유보다_많이_판_만큼만_채우고_뒤의_매매는_그대로_센다() {
        List<PositionGaps.Gap> gaps = PositionGaps.oversells(List.of(
                trade(D1, "AAPL", Side.BUY, "2", "100"),
                trade(D1, "AAPL", Side.SELL, "5", "110"),
                trade(D2, "AAPL", Side.BUY, "1", "100"),
                trade(D2, "AAPL", Side.SELL, "1", "105")));

        assertThat(gaps).singleElement().satisfies(g -> assertThat(g.quantity()).isEqualByComparingTo("3"));
    }

    @Test
    void 체결로_설명되면_추정_입고는_없다() {
        assertThat(PositionGaps.oversells(List.of(
                trade(D1, "005930", Side.BUY, "1.5", "70000"),
                trade(D2, "005930", Side.SELL, "1.5", "71000")))).isEmpty();
    }
}
