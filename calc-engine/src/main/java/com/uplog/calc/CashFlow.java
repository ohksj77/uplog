package com.uplog.calc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** 입금은 양수, 출금은 음수. */
public record CashFlow(LocalDate date, BigDecimal amount) {

    public CashFlow {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(amount, "amount");
    }
}
