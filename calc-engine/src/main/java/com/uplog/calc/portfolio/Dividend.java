package com.uplog.calc.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** 계좌에 들어온 배당(원, 세후). @param date 지급일 */
public record Dividend(LocalDate date, String instrument, BigDecimal amount) {

    public Dividend {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(instrument, "instrument");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("배당은 음수일 수 없습니다: " + amount);
        }
    }
}
