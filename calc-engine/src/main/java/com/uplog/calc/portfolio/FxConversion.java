package com.uplog.calc.portfolio;

import com.uplog.calc.PriceSeries;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** 현지 통화 시계열을 원화로 바꾼다. 그림자 계좌에서 S&P500을 원화로 사는 데 쓴다. */
public final class FxConversion {

    private FxConversion() {}

    /** 날마다 종가 × 그날(없으면 직전) 환율. 첫 환율 전의 날은 버린다. */
    public static PriceSeries toKrw(PriceSeries local, PriceSeries rate) {
        Map<LocalDate, BigDecimal> converted = new TreeMap<>();
        local.range(local.firstDate(), local.lastDate()).forEach((date, close) -> {
            Optional<BigDecimal> fx = rate.closeOnOrBefore(date);
            fx.ifPresent(f -> converted.put(date, close.multiply(f)));
        });
        return new PriceSeries(converted);
    }
}
