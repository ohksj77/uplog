package com.uplog.calc.portfolio;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * 이동평균법 보유분. 원가는 현지 통화와 원화(매수 환율 적용)로 함께 쌓는다.
 * 매도하면 두 원가를 수량 비율대로 줄이므로 평균단가·평균 환율은 바뀌지 않는다. 수수료·세금은 원가에 넣지 않는다.
 *
 * @param costLocal 보유분의 매수 원가(현지 통화) = 수량 × 평균단가
 * @param costKrw 보유분의 매수 원가(원) = Σ 매수 수량 × 가격 × 체결 환율(이동평균)
 */
public record Position(BigDecimal quantity, BigDecimal costLocal, BigDecimal costKrw) {

    public static final Position EMPTY = new Position(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    public boolean isOpen() {
        return quantity.signum() > 0;
    }

    public Position buy(BigDecimal qty, BigDecimal local, BigDecimal krw) {
        return new Position(quantity.add(qty), costLocal.add(local), costKrw.add(krw));
    }

    /** qty만큼 판 뒤의 보유분. 다 팔면 원가도 0으로 비운다. */
    public Position sell(BigDecimal qty) {
        if (qty.compareTo(quantity) > 0) {
            throw new IllegalArgumentException("보유 수량보다 많이 팔 수 없습니다: " + quantity + " < " + qty);
        }
        BigDecimal left = quantity.subtract(qty);
        if (left.signum() == 0) {
            return EMPTY;
        }
        BigDecimal ratio = left.divide(quantity, MathContext.DECIMAL64);
        return new Position(left, costLocal.multiply(ratio), costKrw.multiply(ratio));
    }

    /** qty만큼의 원화 원가(매도분 원가). */
    public BigDecimal costKrwOf(BigDecimal qty) {
        return quantity.signum() == 0 ? BigDecimal.ZERO : costKrw.multiply(qty).divide(quantity, MathContext.DECIMAL64);
    }

    public BigDecimal avgCostLocal() {
        return quantity.signum() == 0 ? BigDecimal.ZERO : costLocal.divide(quantity, MathContext.DECIMAL64);
    }

    /** 매수 금액 가중 평균 환율. 원화 종목은 1. */
    public BigDecimal avgFxRate() {
        return costLocal.signum() == 0 ? BigDecimal.ONE : costKrw.divide(costLocal, MathContext.DECIMAL64);
    }
}
