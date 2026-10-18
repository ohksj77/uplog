package com.uplog.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** 외부 소스에서 받은 ETF 하루 시세. @param code 단축코드(예: 069500) */
public record EtfQuote(LocalDate date, String code, String name, BigDecimal close) {

    public EtfQuote {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(close, "close");
    }
}
