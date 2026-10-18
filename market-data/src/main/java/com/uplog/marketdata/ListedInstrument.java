package com.uplog.marketdata;

import java.util.Objects;
import java.util.Optional;

/** 종목과 시세 보유 구간. 시세가 하나도 없으면 range가 비어 있다. */
public record ListedInstrument(Instrument instrument, Optional<PriceRange> range) {

    public ListedInstrument {
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(range, "range");
    }
}
