package com.uplog.calc.portfolio;

import com.uplog.calc.PriceSeries;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 판 뒤의 그림자: 매도 뒤 주가를 이어 보고 매도 판단을 채점한다.
 * horizon 거래일 뒤 종가가 매도가 이하면 GOOD(판 게 나았음), 초과면 BAD, 아직 안 지났으면 PENDING.
 */
public final class SaleShadow {

    public static final List<Integer> HORIZONS = List.of(5, 20, 60);

    private SaleShadow() {}

    public enum Verdict {
        GOOD,
        BAD,
        PENDING
    }

    /**
     * @param changeAfter 거래일 수(5·20·60) → 매도 N거래일 뒤 종가 / 매도가 − 1. 아직 안 지났으면 empty.
     * @param afterEffect −수량 × (horizon 뒤 종가 − 매도가) × 그날 환율(원 반올림). +면 피한 손실, −면 놓친 이익. PENDING이면 0.
     */
    public record Judged(
            RealizedSale sale,
            BigDecimal priceNowLocal,
            double changeSinceSale,
            Map<Integer, Optional<Double>> changeAfter,
            Verdict verdict,
            long afterEffect) {}

    /** @param avoidedLoss GOOD들의 afterEffect 합, missedGain BAD들의 afterEffect 절댓값 합 */
    public record Summary(int sales, int judged, int good, double winRate, long avoidedLoss, long missedGain) {}

    /**
     * @param prices 그 종목 현지 종가
     * @param usdKrw 달러 종목이면 USD/KRW, 원화 종목이면 null
     * @param until 계좌 마지막 날. 이 뒤의 시세는 보지 않는다.
     */
    public static Judged judge(RealizedSale sale, PriceSeries prices, PriceSeries usdKrw, LocalDate until, int horizon) {
        if (!HORIZONS.contains(horizon)) {
            throw new IllegalArgumentException("horizon은 5, 20, 60 중 하나여야 합니다: " + horizon);
        }
        BigDecimal salePrice = sale.execution().priceLocal();
        Map<Integer, Optional<Double>> changes = new LinkedHashMap<>();
        for (int n : HORIZONS) {
            changes.put(n, closeAfter(prices, sale.day(), n, until).map(c -> change(c.close, salePrice)));
        }
        BigDecimal now = prices.closeOnOrBefore(until).orElse(salePrice);
        Optional<Close> at = closeAfter(prices, sale.day(), horizon, until);
        Verdict verdict = at.map(c -> c.close.compareTo(salePrice) <= 0 ? Verdict.GOOD : Verdict.BAD).orElse(Verdict.PENDING);
        long effect = at.map(c -> {
                    BigDecimal fx = usdKrw == null ? BigDecimal.ONE : usdKrw.closeOnOrBefore(c.date).orElseThrow();
                    return sale.execution().quantity().multiply(c.close.subtract(salePrice)).multiply(fx).negate()
                            .setScale(0, RoundingMode.HALF_UP).longValueExact();
                })
                .orElse(0L);
        return new Judged(sale, now, change(now, salePrice), changes, verdict, effect);
    }

    public static Summary summarize(List<Judged> judged) {
        int decided = 0;
        int good = 0;
        long avoided = 0;
        long missed = 0;
        for (Judged j : judged) {
            switch (j.verdict()) {
                case GOOD -> {
                    decided++;
                    good++;
                    avoided += j.afterEffect();
                }
                case BAD -> {
                    decided++;
                    missed += Math.abs(j.afterEffect());
                }
                case PENDING -> {}
            }
        }
        return new Summary(judged.size(), decided, good, decided == 0 ? 0.0 : (double) good / decided, avoided, missed);
    }

    private record Close(LocalDate date, BigDecimal close) {}

    private static Optional<Close> closeAfter(PriceSeries prices, LocalDate day, int n, LocalDate until) {
        return prices.nthTradingDayFrom(day, n)
                .filter(d -> !d.isAfter(until))
                .map(d -> new Close(d, prices.closeOn(d)));
    }

    private static double change(BigDecimal close, BigDecimal base) {
        return close.divide(base, MathContext.DECIMAL64).subtract(BigDecimal.ONE).doubleValue();
    }
}
