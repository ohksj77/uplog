package com.uplog.calc.portfolio;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 손익 폭포수: 기간 손익(= 끝 평가금 − 시작 평가금 − 순입금)을 종목별 주가 효과, 환율 효과, 배당, 수수료, 세금으로 나눈다.
 *
 * <p>주가 효과는 종목의 현지 통화 손익에 "기준 환율"을 곱한 값이다. 기준 환율은 기간 시작에 들고 있던 보유분이면 시작일 환율,
 * 기간 중에 새로 연 포지션이면 매수 환율이고, 추가 매수는 (전날 평가액 × 기준 환율 + 매수 원화 금액) / (전날 평가액 + 매수 현지 금액)으로 섞는다.
 * 원화 손익에서 주가 효과를 뺀 나머지가 환율 효과라서 둘의 합은 정확히 종목 손익이다. 원 단위 반올림 차이는 OTHER 한 줄로 맞춘다.
 */
public final class PnlWaterfall {

    private PnlWaterfall() {}

    public enum Kind {
        INSTRUMENT,
        DIVIDEND,
        FEE,
        TAX,
        FX,
        OTHER
    }

    /** @param instrument INSTRUMENT일 때만 종목코드, 나머지는 null */
    public record Item(Kind kind, String instrument, long amount) {}

    /** @param total endValue − startValue − netCashFlow = Σ items.amount */
    public record Result(
            LocalDate from, LocalDate to, long startValue, long netCashFlow, long endValue, List<Item> items, long total) {

        public Result {
            items = List.copyOf(items);
        }
    }

    /** (fromIndex, toIndex] 구간. fromIndex 날의 종가가 출발점이다. */
    public static Result of(PortfolioHistory history, int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex >= history.days().size() || fromIndex > toIndex) {
            throw new IllegalArgumentException("구간이 잘못됐습니다: " + fromIndex + " ~ " + toIndex);
        }
        Map<String, BigDecimal> priceEffect = new TreeMap<>();
        Map<String, BigDecimal> reference = new HashMap<>();
        BigDecimal fxEffect = BigDecimal.ZERO;
        BigDecimal dividends = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        BigDecimal taxes = BigDecimal.ZERO;
        BigDecimal netCashFlow = BigDecimal.ZERO;
        for (PortfolioDay day : history.days().subList(fromIndex + 1, toIndex + 1)) {
            netCashFlow = netCashFlow.add(day.netCashFlow());
            dividends = dividends.add(day.dividends());
            fees = fees.add(day.fees());
            taxes = taxes.add(day.taxes());
            for (InstrumentDay d : day.instruments().values()) {
                if (!d.hadActivity()) {
                    continue;
                }
                BigDecimal ref = reference(reference.get(d.instrument()), d);
                BigDecimal effect = d.localPnl().multiply(ref);
                priceEffect.merge(d.instrument(), effect, BigDecimal::add);
                fxEffect = fxEffect.add(d.contribution().subtract(effect));
                if (d.position().isOpen()) {
                    reference.put(d.instrument(), ref);
                } else {
                    reference.remove(d.instrument());
                }
            }
        }

        List<Item> items = new ArrayList<>();
        priceEffect.forEach((code, amount) -> items.add(new Item(Kind.INSTRUMENT, code, won(amount))));
        items.sort(Comparator.comparingLong(Item::amount).reversed().thenComparing(Item::instrument));
        items.add(new Item(Kind.DIVIDEND, null, won(dividends)));
        items.add(new Item(Kind.FEE, null, -won(fees)));
        items.add(new Item(Kind.TAX, null, -won(taxes)));
        items.add(new Item(Kind.FX, null, won(fxEffect)));

        long start = won(history.days().get(fromIndex).value());
        long end = won(history.days().get(toIndex).value());
        long flows = won(netCashFlow);
        long total = end - start - flows;
        long residual = total - items.stream().mapToLong(Item::amount).sum();
        if (residual != 0) {
            items.add(new Item(Kind.OTHER, null, residual));
        }
        return new Result(history.days().get(fromIndex).date(), history.days().get(toIndex).date(),
                start, flows, end, items, total);
    }

    private static BigDecimal reference(BigDecimal tracked, InstrumentDay d) {
        BigDecimal ref = tracked;
        if (ref == null && d.previous().isOpen()) {
            ref = d.previousFxRate();
        }
        if (d.buyLocal().signum() > 0) {
            BigDecimal buyFx = d.buyKrw().divide(d.buyLocal(), MathContext.DECIMAL64);
            if (ref == null || !d.previous().isOpen()) {
                ref = buyFx;
            } else {
                BigDecimal existing = d.previous().quantity().multiply(d.previousPriceLocal());
                ref = existing.multiply(ref).add(d.buyKrw())
                        .divide(existing.add(d.buyLocal()), MathContext.DECIMAL64);
            }
        }
        return ref == null ? d.fxRate() : ref;
    }

    static long won(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
