package com.uplog.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** @param adjClose 수정종가(없으면 null), dividend 그날 배당(없으면 null) */
public record DailyPrice(LocalDate date, BigDecimal close, BigDecimal adjClose, BigDecimal dividend) {

    public DailyPrice {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(close, "close");
    }

    public static DailyPrice close(LocalDate date, BigDecimal close) {
        return new DailyPrice(date, close, null, null);
    }
}
