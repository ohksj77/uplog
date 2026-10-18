package com.uplog.marketdata;

import com.uplog.core.CurrencyCode;

public record NewInstrument(
        String code, String name, String market, CurrencyCode currency, InstrumentType type, boolean sample) {}
