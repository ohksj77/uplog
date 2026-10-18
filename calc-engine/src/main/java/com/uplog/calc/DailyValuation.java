package com.uplog.calc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 하루 장 마감 기준 평가금액과 그날의 순현금흐름.
 *
 * @param value 현금흐름 반영 후 장 마감 평가금액
 * @param netCashFlow 그날 순입금(입금 +, 출금 −)
 */
public record DailyValuation(LocalDate date, BigDecimal value, BigDecimal netCashFlow) {

    public DailyValuation {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(netCashFlow, "netCashFlow");
    }
}
