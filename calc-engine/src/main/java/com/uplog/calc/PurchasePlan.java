package com.uplog.calc;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;

/**
 * 기능 A의 매수 방식: 거치식(시작일 일시 매수) 또는 적립식(매일·매주·매월).
 * 매수일이 휴장일이면 다음 거래일에 사고, 같은 날로 겹친 회차는 한 번만 산다.
 */
public sealed interface PurchasePlan {

    /** @param tradingDays 시뮬레이션 구간의 거래일, 오름차순 */
    List<CashFlow> cashFlows(NavigableSet<LocalDate> tradingDays);

    record LumpSum(BigDecimal amount) implements PurchasePlan {

        public LumpSum {
            requirePositive(amount);
        }

        @Override
        public List<CashFlow> cashFlows(NavigableSet<LocalDate> tradingDays) {
            return tradingDays.isEmpty() ? List.of() : List.of(new CashFlow(tradingDays.first(), amount));
        }
    }

    record Daily(BigDecimal amountPerPurchase) implements PurchasePlan {

        public Daily {
            requirePositive(amountPerPurchase);
        }

        @Override
        public List<CashFlow> cashFlows(NavigableSet<LocalDate> tradingDays) {
            return tradingDays.stream().map(d -> new CashFlow(d, amountPerPurchase)).toList();
        }
    }

    record Weekly(BigDecimal amountPerPurchase, DayOfWeek dayOfWeek) implements PurchasePlan {

        public Weekly {
            requirePositive(amountPerPurchase);
            Objects.requireNonNull(dayOfWeek, "dayOfWeek");
        }

        @Override
        public List<CashFlow> cashFlows(NavigableSet<LocalDate> tradingDays) {
            if (tradingDays.isEmpty()) {
                return List.of();
            }
            LocalDate first = tradingDays.first();
            List<LocalDate> targets = new ArrayList<>();
            LocalDate d = first.plusDays(Math.floorMod(dayOfWeek.getValue() - first.getDayOfWeek().getValue(), 7));
            for (; !d.isAfter(tradingDays.last()); d = d.plusWeeks(1)) {
                targets.add(d);
            }
            return onTradingDays(targets, tradingDays, amountPerPurchase);
        }
    }

    /** @param dayOfMonth 1–31. 그 달에 없는 날짜면 말일을 매수일로 본다. */
    record Monthly(BigDecimal amountPerPurchase, int dayOfMonth) implements PurchasePlan {

        public Monthly {
            requirePositive(amountPerPurchase);
            if (dayOfMonth < 1 || dayOfMonth > 31) {
                throw new IllegalArgumentException("dayOfMonth는 1–31이어야 합니다: " + dayOfMonth);
            }
        }

        @Override
        public List<CashFlow> cashFlows(NavigableSet<LocalDate> tradingDays) {
            if (tradingDays.isEmpty()) {
                return List.of();
            }
            List<LocalDate> targets = new ArrayList<>();
            YearMonth last = YearMonth.from(tradingDays.last());
            for (YearMonth ym = YearMonth.from(tradingDays.first()); !ym.isAfter(last); ym = ym.plusMonths(1)) {
                LocalDate d = ym.atDay(Math.min(dayOfMonth, ym.lengthOfMonth()));
                if (!d.isBefore(tradingDays.first())) {
                    targets.add(d);
                }
            }
            return onTradingDays(targets, tradingDays, amountPerPurchase);
        }
    }

    private static List<CashFlow> onTradingDays(
            List<LocalDate> targets, NavigableSet<LocalDate> tradingDays, BigDecimal amount) {
        Set<LocalDate> days = new LinkedHashSet<>();
        for (LocalDate target : targets) {
            LocalDate day = tradingDays.ceiling(target);
            if (day != null) {
                days.add(day);
            }
        }
        return days.stream().map(d -> new CashFlow(d, amount)).toList();
    }

    private static void requirePositive(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("매수 금액은 양수여야 합니다: " + amount);
        }
    }
}
