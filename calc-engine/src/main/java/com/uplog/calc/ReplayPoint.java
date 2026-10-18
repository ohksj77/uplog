package com.uplog.calc;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 벤치마크 리플레이의 하루.
 *
 * @param units 보유 수량(소수 허용)
 * @param value 장 마감 평가금액
 * @param invested 누적 순투입 원금
 * @param netCashFlow 그날 실제로 체결된 순현금흐름
 */
public record ReplayPoint(
        LocalDate date, BigDecimal close, BigDecimal units, BigDecimal value, BigDecimal invested, BigDecimal netCashFlow) {

    public DailyValuation toValuation() {
        return new DailyValuation(date, value, netCashFlow);
    }
}
