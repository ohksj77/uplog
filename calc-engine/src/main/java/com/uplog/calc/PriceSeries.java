package com.uplog.calc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeMap;

/** 한 종목의 일별 종가. 키가 곧 그 시장의 거래일이다. */
public final class PriceSeries {

    private final NavigableMap<LocalDate, BigDecimal> closes;

    public PriceSeries(Map<LocalDate, BigDecimal> closes) {
        if (closes.isEmpty()) {
            throw new IllegalArgumentException("시세가 비어 있습니다");
        }
        closes.forEach((date, close) -> {
            if (close.signum() <= 0) {
                throw new IllegalArgumentException("종가는 양수여야 합니다: " + date + " " + close);
            }
        });
        this.closes = Collections.unmodifiableNavigableMap(new TreeMap<>(closes));
    }

    public LocalDate firstDate() {
        return closes.firstKey();
    }

    public LocalDate lastDate() {
        return closes.lastKey();
    }

    /** date가 거래일이면 그날, 아니면 다음 거래일. 시세 범위를 넘으면 empty. */
    public Optional<LocalDate> tradingDayOnOrAfter(LocalDate date) {
        return Optional.ofNullable(closes.ceilingKey(date));
    }

    public BigDecimal closeOn(LocalDate date) {
        BigDecimal close = closes.get(date);
        if (close == null) {
            throw new IllegalArgumentException("거래일이 아닙니다: " + date);
        }
        return close;
    }

    /** [from, to] 구간의 종가, 날짜 오름차순. */
    public NavigableMap<LocalDate, BigDecimal> range(LocalDate from, LocalDate to) {
        return closes.subMap(from, true, to, true);
    }

    /** [from, to] 구간의 거래일, 오름차순. */
    public NavigableSet<LocalDate> tradingDays(LocalDate from, LocalDate to) {
        return closes.navigableKeySet().subSet(from, true, to, true);
    }
}
