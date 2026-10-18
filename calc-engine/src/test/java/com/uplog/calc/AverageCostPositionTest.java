package com.uplog.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uplog.calc.Trade.Side;
import com.uplog.core.CurrencyCode;
import com.uplog.core.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AverageCostPositionTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 12);

    private static Money usd(String amount) {
        return Money.of(amount, CurrencyCode.USD);
    }

    private static Trade trade(Side side, int qty, String price, String fee, String tax) {
        return new Trade(DAY, side, BigDecimal.valueOf(qty), usd(price), usd(fee), usd(tax));
    }

    @Test
    void 매수하면_평균단가가_이동한다_수수료는_넣지_않는다() {
        AverageCostPosition p = AverageCostPosition.empty(CurrencyCode.USD)
                .apply(trade(Side.BUY, 10, "100", "1", "0"))
                .apply(trade(Side.BUY, 10, "200", "0", "0"));

        assertThat(p.quantity()).isEqualByComparingTo("20");
        assertThat(p.averageCost()).isEqualTo(usd("150"));
        assertThat(p.realizedPnl()).isEqualTo(usd("0"));
        assertThat(p.costs()).isEqualTo(usd("1"));
    }

    @Test
    void 매도하면_평균단가는_유지하고_매도_비용을_뺀_실현손익을_쌓는다() {
        AverageCostPosition p = AverageCostPosition.empty(CurrencyCode.USD)
                .apply(trade(Side.BUY, 10, "100", "1", "0"))
                .apply(trade(Side.BUY, 10, "200", "0", "0"))
                .apply(trade(Side.SELL, 5, "300", "2", "3"));

        assertThat(p.quantity()).isEqualByComparingTo("15");
        assertThat(p.averageCost()).isEqualTo(usd("150"));
        assertThat(p.realizedPnl()).isEqualTo(usd("745"));
        assertThat(p.costs()).isEqualTo(usd("6"));
        assertThat(p.unrealizedPnl(usd("160"))).isEqualTo(usd("150"));
    }

    @Test
    void 전량_매도하면_평균단가를_초기화한다() {
        AverageCostPosition p = AverageCostPosition.empty(CurrencyCode.USD)
                .apply(trade(Side.BUY, 3, "100", "0", "0"))
                .apply(trade(Side.SELL, 3, "90", "0", "0"));

        assertThat(p.quantity()).isEqualByComparingTo("0");
        assertThat(p.averageCost()).isEqualTo(usd("0"));
        assertThat(p.realizedPnl()).isEqualTo(usd("-30"));
    }

    @Test
    void 보유보다_많이_팔_수_없다() {
        AverageCostPosition p = AverageCostPosition.empty(CurrencyCode.USD).apply(trade(Side.BUY, 1, "100", "0", "0"));

        assertThatThrownBy(() -> p.apply(trade(Side.SELL, 2, "100", "0", "0")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
