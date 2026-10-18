package com.uplog.calc;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 고통 지표.
 *
 * @param maxDrawdown TWR 지수 기준 고점 대비 최대 하락률(0 이하)
 * @param longestUnderwater 평가금액이 순투입 원금 아래에 머문 가장 긴 구간
 * @param worstDay 일별 수익률이 가장 낮았던 날
 */
public record PainMetrics(Drawdown maxDrawdown, Optional<Underwater> longestUnderwater, DailyReturn worstDay) {

    /** @param peak 하락이 시작된 고점일, trough 바닥일 */
    public record Drawdown(double rate, LocalDate peak, LocalDate trough) {}

    /** @param end 원금을 회복한 날. 기간 끝까지 회복하지 못했으면 마지막 날이고 recovered = false. */
    public record Underwater(LocalDate start, LocalDate end, boolean recovered) {

        public long days() {
            return ChronoUnit.DAYS.between(start, end);
        }
    }

    public static PainMetrics of(List<ReplayPoint> points, List<DailyReturn> returns) {
        if (points.isEmpty() || points.size() != returns.size()) {
            throw new IllegalArgumentException("평가 시계열과 수익률 시계열의 길이가 맞지 않습니다");
        }
        return new PainMetrics(maxDrawdown(returns), longestUnderwater(points), worstDay(returns));
    }

    private static Drawdown maxDrawdown(List<DailyReturn> returns) {
        DailyReturn peak = returns.getFirst();
        Drawdown worst = new Drawdown(0.0, peak.date(), peak.date());
        for (DailyReturn r : returns) {
            if (r.twrIndex() > peak.twrIndex()) {
                peak = r;
            }
            double dd = r.twrIndex() / peak.twrIndex() - 1.0;
            if (dd < worst.rate()) {
                worst = new Drawdown(dd, peak.date(), r.date());
            }
        }
        return worst;
    }

    private static Optional<Underwater> longestUnderwater(List<ReplayPoint> points) {
        Underwater longest = null;
        LocalDate start = null;
        for (ReplayPoint p : points) {
            boolean under = p.value().compareTo(p.invested()) < 0;
            if (under && start == null) {
                start = p.date();
            } else if (!under && start != null) {
                longest = longer(longest, new Underwater(start, p.date(), true));
                start = null;
            }
        }
        if (start != null) {
            longest = longer(longest, new Underwater(start, points.getLast().date(), false));
        }
        return Optional.ofNullable(longest);
    }

    private static Underwater longer(Underwater current, Underwater candidate) {
        return current == null || candidate.days() > current.days() ? candidate : current;
    }

    private static DailyReturn worstDay(List<DailyReturn> returns) {
        DailyReturn worst = returns.getFirst();
        for (DailyReturn r : returns) {
            if (r.dailyReturn() < worst.dailyReturn()) {
                worst = r;
            }
        }
        return worst;
    }
}
