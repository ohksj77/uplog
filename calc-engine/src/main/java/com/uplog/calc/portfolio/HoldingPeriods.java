package com.uplog.calc.portfolio;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 라운드트립 포지션: 한 종목의 보유 수량이 0에서 양수가 된 날부터 다시 0이 된 날까지.
 * 수익률 = (매도 금액 + 현재 평가금 + 배당 − 매수 금액 − 수수료 − 세금) / 매수 금액, 모두 원화.
 */
public final class HoldingPeriods {

    private HoldingPeriods() {}

    /** 보유 기간(달력일) 구간. 이 순서로 화면에 늘 네 칸을 그린다. */
    public enum Bucket {
        SHORT("단타 (7일 이하)", 0, 7),
        SWING("스윙 (8–90일)", 8, 90),
        MID("중기 (91–365일)", 91, 365),
        LONG("장투 (1년 초과)", 366, Long.MAX_VALUE);

        private final String label;
        private final long minDays;
        private final long maxDays;

        Bucket(String label, long minDays, long maxDays) {
            this.label = label;
            this.minDays = minDays;
            this.maxDays = maxDays;
        }

        public String label() {
            return label;
        }

        public static Bucket of(long days) {
            for (Bucket b : values()) {
                if (days >= b.minDays && days <= b.maxDays) {
                    return b;
                }
            }
            return SHORT;
        }
    }

    /**
     * @param close 다시 0이 된 날. 아직 보유 중이면 empty.
     * @param days close(보유 중이면 마지막 날) − open, 달력일
     * @param buyAmount·sellAmount 매수·매도 금액(원, 비용 제외) / currentValue 보유 중이면 마지막 날 평가금
     */
    public record Period(
            String instrument,
            LocalDate open,
            Optional<LocalDate> close,
            long days,
            BigDecimal buyAmount,
            BigDecimal sellAmount,
            BigDecimal currentValue,
            BigDecimal dividends,
            BigDecimal fees,
            BigDecimal taxes) {

        public boolean isOpen() {
            return close.isEmpty();
        }

        public BigDecimal profit() {
            return sellAmount.add(currentValue).add(dividends).subtract(buyAmount).subtract(fees).subtract(taxes);
        }

        public double returnRate() {
            return buyAmount.signum() == 0 ? 0.0 : profit().divide(buyAmount, MathContext.DECIMAL64).doubleValue();
        }

        public Bucket bucket() {
            return Bucket.of(days);
        }
    }

    private static final class Running {
        final String instrument;
        final LocalDate open;
        BigDecimal buy = BigDecimal.ZERO;
        BigDecimal sell = BigDecimal.ZERO;
        BigDecimal dividends = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        BigDecimal taxes = BigDecimal.ZERO;

        Running(String instrument, LocalDate open) {
            this.instrument = instrument;
            this.open = open;
        }

        void add(InstrumentDay d) {
            buy = buy.add(d.buyKrw());
            sell = sell.add(d.sellKrw());
            dividends = dividends.add(d.dividends());
            fees = fees.add(d.fees());
            taxes = taxes.add(d.taxes());
        }

        Period toPeriod(LocalDate close, LocalDate last, BigDecimal currentValue) {
            LocalDate end = close == null ? last : close;
            return new Period(instrument, open, Optional.ofNullable(close), ChronoUnit.DAYS.between(open, end),
                    buy, sell, currentValue, dividends, fees, taxes);
        }
    }

    /** 모든 포지션, open 오름차순(같으면 종목코드순). 포지션 밖에서 들어온 배당은 그 종목의 직전 포지션에 붙인다. */
    public static List<Period> of(PortfolioHistory history) {
        Map<String, Running> open = new HashMap<>();
        Map<String, Integer> lastClosed = new HashMap<>();
        List<Period> periods = new ArrayList<>();
        LocalDate last = history.last().date();
        for (PortfolioDay day : history.days()) {
            for (InstrumentDay d : day.instruments().values()) {
                Running running = open.get(d.instrument());
                if (running == null && d.buyQuantity().signum() > 0) {
                    running = new Running(d.instrument(), day.date());
                    open.put(d.instrument(), running);
                }
                if (running == null) {
                    Integer closed = lastClosed.get(d.instrument());
                    if (closed != null && d.dividends().signum() > 0) {
                        Period p = periods.get(closed);
                        periods.set(closed, new Period(p.instrument(), p.open(), p.close(), p.days(), p.buyAmount(),
                                p.sellAmount(), p.currentValue(), p.dividends().add(d.dividends()), p.fees(), p.taxes()));
                    }
                    continue;
                }
                running.add(d);
                if (!d.position().isOpen()) {
                    lastClosed.put(d.instrument(), periods.size());
                    periods.add(running.toPeriod(day.date(), last, BigDecimal.ZERO));
                    open.remove(d.instrument());
                }
            }
        }
        PortfolioDay lastDay = history.last();
        open.values().forEach(r -> periods.add(r.toPeriod(null, last, lastDay.instruments().get(r.instrument).value())));
        periods.sort(Comparator.comparing(Period::open).thenComparing(Period::instrument));
        return List.copyOf(periods);
    }
}
