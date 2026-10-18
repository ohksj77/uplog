package com.uplog.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void 같은_통화끼리_더하고_뺀다() {
        Money a = Money.of(1_000, CurrencyCode.KRW);
        Money b = Money.of(250, CurrencyCode.KRW);

        assertThat(a.plus(b)).isEqualTo(Money.of(1_250, CurrencyCode.KRW));
        assertThat(b.minus(a).isNegative()).isTrue();
    }

    @Test
    void 다른_통화는_섞지_않는다() {
        assertThatThrownBy(() -> Money.of(1, CurrencyCode.KRW).plus(Money.of(1, CurrencyCode.USD)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 금액_비교는_scale을_무시한다() {
        assertThat(Money.of("1000.00", CurrencyCode.KRW)).isEqualTo(Money.of(1_000, CurrencyCode.KRW));
        assertThat(Money.of("1000.00", CurrencyCode.KRW).hashCode())
                .isEqualTo(Money.of(1_000, CurrencyCode.KRW).hashCode());
    }

    @Test
    void 달러를_원화로_환산하고_반올림한다() {
        Money usd = Money.of("12.34", CurrencyCode.USD);

        Money krw = usd.convert(CurrencyCode.KRW, new BigDecimal("1385.5")).rounded();

        assertThat(krw).isEqualTo(Money.of(17_097, CurrencyCode.KRW));
    }
}
