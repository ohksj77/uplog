package com.uplog.calc;

import com.uplog.core.CurrencyCode;
import com.uplog.core.Money;
import java.math.BigDecimal;
import java.math.MathContext;

/**
 * 이동평균법 포지션. 토스 앱에 표시되는 평균단가·실현손익과 맞추기 위해 이 방식을 쓴다.
 *
 * <p>수수료·세금은 평균단가에 넣지 않는다. 매도 쪽 비용은 실현손익에서 빼고, 모든 비용은
 * {@code costs}에 따로 쌓아 "숨은 비용" 집계에 쓴다.
 */
public record AverageCostPosition(BigDecimal quantity, Money averageCost, Money realizedPnl, Money costs) {

    public static AverageCostPosition empty(CurrencyCode currency) {
        Money zero = Money.zero(currency);
        return new AverageCostPosition(BigDecimal.ZERO, zero, zero, zero);
    }

    public AverageCostPosition apply(Trade trade) {
        Money tradeCosts = trade.fee().plus(trade.tax());
        return switch (trade.side()) {
            case BUY -> {
                BigDecimal newQuantity = quantity.add(trade.quantity());
                BigDecimal totalCost = averageCost.amount().multiply(quantity)
                        .add(trade.price().amount().multiply(trade.quantity()));
                Money newAverage = new Money(
                        totalCost.divide(newQuantity, MathContext.DECIMAL64), averageCost.currency());
                yield new AverageCostPosition(newQuantity, newAverage, realizedPnl, costs.plus(tradeCosts));
            }
            case SELL -> {
                if (trade.quantity().compareTo(quantity) > 0) {
                    throw new IllegalArgumentException("보유 수량보다 많이 팔 수 없습니다: " + quantity + " < " + trade.quantity());
                }
                BigDecimal newQuantity = quantity.subtract(trade.quantity());
                Money gain = trade.price().minus(averageCost).times(trade.quantity()).minus(tradeCosts);
                Money newAverage = newQuantity.signum() == 0 ? Money.zero(averageCost.currency()) : averageCost;
                yield new AverageCostPosition(newQuantity, newAverage, realizedPnl.plus(gain), costs.plus(tradeCosts));
            }
        };
    }

    public Money unrealizedPnl(Money price) {
        return price.minus(averageCost).times(quantity);
    }
}
