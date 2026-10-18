package com.uplog.marketdata;

import com.uplog.core.CurrencyCode;
import java.util.Objects;

/** @param sample 로컬 개발용 합성 시세 종목. 화면에 "샘플 데이터"로 표시해야 한다. */
public record Instrument(
        long id, String code, String name, String market, CurrencyCode currency, InstrumentType type, boolean sample) {

    public Instrument {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(market, "market");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(type, "type");
    }
}
