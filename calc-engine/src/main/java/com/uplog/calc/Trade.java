package com.uplog.calc;

import com.uplog.core.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** 체결 한 건. 금액은 모두 종목 통화. */
public record Trade(LocalDate date, Side side, BigDecimal quantity, Money price, Money fee, Money tax) {

    public enum Side {
        BUY,
        SELL
    }

    public Trade {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(quantity, "quantity");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("체결 수량은 양수여야 합니다: " + quantity);
        }
        if (price.currency() != fee.currency() || price.currency() != tax.currency()) {
            throw new IllegalArgumentException("체결 금액의 통화가 섞여 있습니다");
        }
    }
}
