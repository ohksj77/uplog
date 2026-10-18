package com.uplog.calc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 고통 지표. 하락 폭과 가장 아팠던 날은 두 기준으로 낸다.
 * 수익률 기준은 입출금을 뺀 TWR이라 같은 종목이면 매수 방식과 관계없이 같고,
 * 금액 기준은 평가손익(평가금액 − 순투입 원금)이라 방식마다 실제로 겪은 손실이 드러난다.
 *
 * @param maxDrawdown TWR 지수 기준 고점 대비 최대 하락률(0 이하)
 * @param maxProfitDrawdown 평가손익 고점 대비 최대 감소액(0 이하)
 * @param longestUnderwater 평가금액이 순투입 원금 아래에 머문 가장 긴 구간
 * @param worstDay 일별 수익률이 가장 낮았던 날
 * @param worstDayAmount 하루 손익(입출금 제외)이 가장 낮았던 날
 */
public record PainMetrics(
        Drawdown maxDrawdown,
        AmountDrawdown maxProfitDrawdown,
        Optional<Underwater> longestUnderwater,
        DailyReturn worstDay,
        DailyPnl worstDayAmount) {

    /** @param peak 하락이 시작된 고점일, trough 바닥일 */
    public record Drawdown(double rate, LocalDate peak, LocalDate trough) {}

    /** @param amount 고점 대비 감소액(0 이하) */
    public record AmountDrawdown(BigDecimal amount, LocalDate peak, LocalDate trough) {}

    /** @param pnl 그날 손익 = 평가금액 − 그날 현금흐름 − 전날 평가금액 */
    public record DailyPnl(LocalDate date, BigDecimal pnl) {}

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
        return new PainMetrics(
                maxDrawdown(returns),
                maxProfitDrawdown(points),
                longestUnderwater(points),
                worstDay(returns),
                worstDayAmount(points));
    }

    private static AmountDrawdown maxProfitDrawdown(List<ReplayPoint> points) {
        ReplayPoint first = points.getFirst();
        BigDecimal peakProfit = profit(first);
        LocalDate peakDate = first.date();
        AmountDrawdown worst = new AmountDrawdown(BigDecimal.ZERO, peakDate, peakDate);
        for (ReplayPoint p : points) {
            BigDecimal profit = profit(p);
            if (profit.compareTo(peakProfit) > 0) {
                peakProfit = profit;
                peakDate = p.date();
            }
            BigDecimal dd = profit.subtract(peakProfit);
            if (dd.compareTo(worst.amount()) < 0) {
                worst = new AmountDrawdown(dd, peakDate, p.date());
            }
        }
        return worst;
    }

    private static BigDecimal profit(ReplayPoint p) {
        return p.value().subtract(p.invested());
    }

    private static DailyPnl worstDayAmount(List<ReplayPoint> points) {
        DailyPnl worst = null;
        BigDecimal prevValue = BigDecimal.ZERO;
        for (ReplayPoint p : points) {
            BigDecimal pnl = p.value().subtract(p.netCashFlow()).subtract(prevValue);
            if (worst == null || pnl.compareTo(worst.pnl()) < 0) {
                worst = new DailyPnl(p.date(), pnl);
            }
            prevValue = p.value();
        }
        return worst;
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
