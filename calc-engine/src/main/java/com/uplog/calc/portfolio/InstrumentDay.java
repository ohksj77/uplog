package com.uplog.calc.portfolio;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * 한 종목의 하루. 전날 장 마감과 그날 장 마감의 보유분·가격·환율, 그날의 체결·비용·배당.
 *
 * @param previousPriceLocal 전날 종가(현지). 전날 보유하지 않았으면 0일 수 있다.
 * @param previousValue 전날 평가금(원)
 * @param value 그날 평가금(원) = 수량 × 종가 × 환율
 * @param buyKrw 그날 매수 금액(원, 비용 제외) / sellKrw 매도 금액(원, 비용 제외)
 * @param fees 그날 이 종목 체결 수수료(원) / taxes 세금(원) / dividends 배당(원)
 */
public record InstrumentDay(
        String instrument,
        Position previous,
        BigDecimal previousPriceLocal,
        BigDecimal previousFxRate,
        BigDecimal previousValue,
        Position position,
        BigDecimal priceLocal,
        BigDecimal fxRate,
        BigDecimal value,
        BigDecimal buyQuantity,
        BigDecimal buyLocal,
        BigDecimal buyKrw,
        BigDecimal sellQuantity,
        BigDecimal sellLocal,
        BigDecimal sellKrw,
        BigDecimal fees,
        BigDecimal taxes,
        BigDecimal dividends) {

    /** 그날 이 종목 손익(원, 환율 변동 포함, 비용·배당 제외) = 평가금 변화 + 매도 금액 − 매수 금액. */
    public BigDecimal contribution() {
        return value.subtract(previousValue).add(sellKrw).subtract(buyKrw);
    }

    /** 같은 정의의 현지 통화 손익(환율 효과 없음). */
    public BigDecimal localPnl() {
        return position.quantity().multiply(priceLocal)
                .subtract(previous.quantity().multiply(previousPriceLocal))
                .add(sellLocal)
                .subtract(buyLocal);
    }

    /** contribution / (전날 평가금 + 그날 매수 금액). 분모가 0이면 0. */
    public double dailyReturn() {
        BigDecimal base = previousValue.add(buyKrw);
        return base.signum() == 0 ? 0.0 : contribution().divide(base, MathContext.DECIMAL64).doubleValue();
    }

    public boolean hadActivity() {
        return previous.isOpen() || position.isOpen() || buyQuantity.signum() > 0 || sellQuantity.signum() > 0
                || dividends.signum() > 0;
    }

    /** 평가손익(원) = 평가금 − 원화 원가. */
    public BigDecimal unrealizedProfit() {
        return value.subtract(position.costKrw());
    }
}
