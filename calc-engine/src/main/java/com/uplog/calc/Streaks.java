package com.uplog.calc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 연속 기록. 수익·손실은 거래일 단위, 적립은 주기(거래일·주·월) 단위로 센다.
 * 기능 A(시뮬레이션)와 기능 B(내 계좌)가 함께 쓴다.
 */
public final class Streaks {

    private Streaks() {}

    /** @param start 연속이 시작된 날, end 끝난 날(적립은 그 구간의 첫·마지막 매수일) */
    public record Streak(int length, LocalDate start, LocalDate end) {}

    /** @param current 마지막 날까지 이어지고 있는 연속. 없으면 empty. */
    public record Stats(Optional<Streak> longest, Optional<Streak> current) {

        static final Stats NONE = new Stats(Optional.empty(), Optional.empty());
    }

    public enum Unit {
        /** 거래일 */
        DAY,
        /** 월요일 시작 주 */
        WEEK,
        MONTH
    }

    /** 그날 수익률이 0보다 큰 거래일이 이어진 기록(로그의 빨간 칸). */
    public static Stats gains(List<DailyReturn> returns) {
        return runs(returns, r -> r.dailyReturn() > 0, DailyReturn::date, false);
    }

    /** 그날 수익률이 0보다 작은 거래일이 이어진 기록(로그의 파란 칸). */
    public static Stats losses(List<DailyReturn> returns) {
        return runs(returns, r -> r.dailyReturn() < 0, DailyReturn::date, false);
    }

    /**
     * 매수가 한 번 이상 있었던 주기가 이어진 기록. 주기 사이에 거래일이 없는 주·월은 건너뛴다.
     * 마지막 주기에 아직 매수가 없으면(예: 이번 달 적립일이 아직 안 옴) 직전 주기까지의 연속을 현재 연속으로 본다.
     */
    public static Stats purchases(List<ReplayPoint> points, Unit unit) {
        Map<LocalDate, List<LocalDate>> buysByPeriod = new LinkedHashMap<>();
        for (ReplayPoint p : points) {
            List<LocalDate> buys = buysByPeriod.computeIfAbsent(periodOf(p.date(), unit), k -> new ArrayList<>());
            if (p.netCashFlow().signum() > 0) {
                buys.add(p.date());
            }
        }
        List<List<LocalDate>> periods = List.copyOf(buysByPeriod.values());
        return runs(periods, buys -> !buys.isEmpty(), List::getFirst, true, List::getLast);
    }

    private static LocalDate periodOf(LocalDate date, Unit unit) {
        return switch (unit) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private static <T> Stats runs(
            List<T> items, Predicate<T> hit, Function<T, LocalDate> date, boolean graceForLast) {
        return runs(items, hit, date, graceForLast, date);
    }

    private static <T> Stats runs(
            List<T> items,
            Predicate<T> hit,
            Function<T, LocalDate> startDate,
            boolean graceForLast,
            Function<T, LocalDate> endDate) {
        if (items.isEmpty()) {
            return Stats.NONE;
        }
        Streak longest = null;
        Streak running = null;
        for (T item : items) {
            if (hit.test(item)) {
                running = running == null
                        ? new Streak(1, startDate.apply(item), endDate.apply(item))
                        : new Streak(running.length() + 1, running.start(), endDate.apply(item));
                if (longest == null || running.length() > longest.length()) {
                    longest = running;
                }
            } else {
                running = null;
            }
        }
        Streak current = running;
        if (current == null && graceForLast && items.size() >= 2) {
            current = runs(items.subList(0, items.size() - 1), hit, startDate, false, endDate)
                    .current()
                    .orElse(null);
        }
        return new Stats(Optional.ofNullable(longest), Optional.ofNullable(current));
    }
}
