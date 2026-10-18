package com.uplog.simulation;

import java.math.BigDecimal;

/**
 * 거치식과 적립식이 같은 총 투입금을 쓰도록 금액을 정한다.
 *
 * <ul>
 *   <li>TOTAL: 회차당 = amount / 회차 수(원 단위 내림), 거치식 = 회차당 × 회차 수(적립식 실제 총 투입금)
 *   <li>PER_PURCHASE: 회차당 = amount, 거치식 = amount × 회차 수
 * </ul>
 */
public record PlanAmounts(long perPurchase, int purchaseCount, long lumpSum) {

    public static PlanAmounts of(AmountType type, long amount, int purchaseCount) {
        if (purchaseCount <= 0) {
            throw new IllegalArgumentException("회차가 없습니다");
        }
        long perPurchase = switch (type) {
            case TOTAL -> amount / purchaseCount;
            case PER_PURCHASE -> amount;
        };
        if (perPurchase < 1) {
            throw SimulationException.invalid(
                    "회차당 금액이 1원보다 작습니다. amount를 늘리거나 적립 주기를 늘려 주세요(회차 " + purchaseCount + "번).");
        }
        return new PlanAmounts(perPurchase, purchaseCount, Math.multiplyExact(perPurchase, purchaseCount));
    }

    public BigDecimal perPurchaseAmount() {
        return BigDecimal.valueOf(perPurchase);
    }

    public BigDecimal lumpSumAmount() {
        return BigDecimal.valueOf(lumpSum);
    }
}
