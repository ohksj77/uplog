package com.uplog.calc.portfolio;

import com.uplog.calc.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 체결 한 건. 가격·수수료·세금은 종목 통화(현지) 값이고, fxRate는 그 체결에 적용된 원화 환율(원화 종목은 1)이다.
 *
 * @param instrument 종목코드
 */
public record Execution(
        LocalDate date,
        String instrument,
        Trade.Side side,
        BigDecimal quantity,
        BigDecimal priceLocal,
        BigDecimal fxRate,
        BigDecimal feeLocal,
        BigDecimal taxLocal) {

    public Execution {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(side, "side");
        if (quantity.signum() <= 0 || priceLocal.signum() <= 0 || fxRate.signum() <= 0) {
            throw new IllegalArgumentException("수량·가격·환율은 양수여야 합니다: " + instrument + " " + date);
        }
        if (feeLocal.signum() < 0 || taxLocal.signum() < 0) {
            throw new IllegalArgumentException("수수료·세금은 음수일 수 없습니다: " + instrument + " " + date);
        }
    }

    public BigDecimal grossLocal() {
        return quantity.multiply(priceLocal);
    }

    /** 수량 × 가격 × 환율(원). 비용은 넣지 않는다. */
    public BigDecimal grossKrw() {
        return grossLocal().multiply(fxRate);
    }

    public BigDecimal feeKrw() {
        return feeLocal.multiply(fxRate);
    }

    public BigDecimal taxKrw() {
        return taxLocal.multiply(fxRate);
    }
}
