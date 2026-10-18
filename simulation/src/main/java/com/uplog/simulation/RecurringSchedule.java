package com.uplog.simulation;

import com.uplog.calc.PurchasePlan;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.NavigableSet;
import java.util.Objects;

/**
 * 적립 주기. 주기에 맞지 않는 필드는 null로 정규화한다(캐시 키가 같아지도록).
 *
 * @param dayOfWeek WEEKLY일 때만
 * @param dayOfMonth MONTHLY일 때만, 1–31
 */
public record RecurringSchedule(Frequency frequency, DayOfWeek dayOfWeek, Integer dayOfMonth) {

    public RecurringSchedule {
        Objects.requireNonNull(frequency, "frequency");
        if ((frequency == Frequency.WEEKLY) != (dayOfWeek != null)) {
            throw new IllegalArgumentException("dayOfWeek는 WEEKLY일 때만 둡니다");
        }
        if ((frequency == Frequency.MONTHLY) != (dayOfMonth != null)) {
            throw new IllegalArgumentException("dayOfMonth는 MONTHLY일 때만 둡니다");
        }
    }

    public static RecurringSchedule daily() {
        return new RecurringSchedule(Frequency.DAILY, null, null);
    }

    public static RecurringSchedule weekly(DayOfWeek dayOfWeek) {
        return new RecurringSchedule(Frequency.WEEKLY, dayOfWeek, null);
    }

    public static RecurringSchedule monthly(int dayOfMonth) {
        return new RecurringSchedule(Frequency.MONTHLY, null, dayOfMonth);
    }

    public PurchasePlan plan(BigDecimal amountPerPurchase) {
        return switch (frequency) {
            case DAILY -> new PurchasePlan.Daily(amountPerPurchase);
            case WEEKLY -> new PurchasePlan.Weekly(amountPerPurchase, dayOfWeek);
            case MONTHLY -> new PurchasePlan.Monthly(amountPerPurchase, dayOfMonth);
        };
    }

    /** 이 구간에서 실제로 사게 되는 회차 수(휴장일로 밀려 겹친 회차는 하나로 센다). */
    public int purchaseCount(NavigableSet<LocalDate> tradingDays) {
        return plan(BigDecimal.ONE).cashFlows(tradingDays).size();
    }
}
