package com.uplog.calc.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 수중 차트. 수익률 기준은 TWR 지수의 직전 고점 대비 하락률, 금액 기준은 평가손익(평가금 − 순입금)의 직전 고점 대비 감소액.
 * 구간(episode)은 TWR 기준으로 고점 아래로 내려갔다가 고점을 되찾은(또는 아직 못 찾은) 구간이다.
 */
public final class Underwater {

    private Underwater() {}

    /** @param drawdown 0 이하 / drawdownAmount 0 이하(원) */
    public record Point(LocalDate date, double drawdown, BigDecimal drawdownAmount) {}

    /** @param recovered 고점을 되찾은 날. 아직이면 empty. */
    public record Episode(LocalDate peak, LocalDate trough, Optional<LocalDate> recovered, double depth) {

        public long daysToTrough() {
            return ChronoUnit.DAYS.between(peak, trough);
        }

        /** 바닥에서 회복까지 달력일. */
        public Optional<Long> daysToRecover() {
            return recovered.map(r -> ChronoUnit.DAYS.between(trough, r));
        }
    }

    /**
     * @param peak 지금 고점 아래면 그 구간의 고점일, 고점이면 마지막 날
     * @param days 마지막 날 − peak(달력일)
     * @param deepestPast 현재 구간을 뺀 과거 구간 중 가장 깊은 값(없으면 0)
     */
    public record Current(double drawdown, LocalDate peak, long days, double deepestPast, boolean deeperThanPast) {}

    /** @param episodes 깊은 순 */
    public record Result(List<Point> series, List<Episode> episodes, Current current) {

        public Result {
            series = List.copyOf(series);
            episodes = List.copyOf(episodes);
        }
    }

    /** @param observed 기록일(MISSING 제외), 날짜순 */
    public static Result of(List<AccountLog.Entry> observed) {
        if (observed.isEmpty()) {
            throw new IllegalArgumentException("기록이 비어 있습니다");
        }
        List<Point> series = new ArrayList<>(observed.size());
        List<Episode> episodes = new ArrayList<>();
        double peakIndex = Double.NEGATIVE_INFINITY;
        LocalDate peakDate = observed.getFirst().date();
        BigDecimal peakProfit = null;
        Episode open = null;
        for (AccountLog.Entry e : observed) {
            BigDecimal profit = e.profit();
            peakProfit = peakProfit == null || profit.compareTo(peakProfit) > 0 ? profit : peakProfit;
            BigDecimal amount = profit.subtract(peakProfit);
            if (e.twrIndex() >= peakIndex) {
                if (open != null) {
                    episodes.add(new Episode(open.peak(), open.trough(), Optional.of(e.date()), open.depth()));
                    open = null;
                }
                peakIndex = e.twrIndex();
                peakDate = e.date();
                series.add(new Point(e.date(), 0.0, amount));
                continue;
            }
            double dd = e.twrIndex() / peakIndex - 1.0;
            if (open == null || dd < open.depth()) {
                open = new Episode(peakDate, e.date(), Optional.empty(), dd);
            }
            series.add(new Point(e.date(), dd, amount));
        }
        double deepestPast = episodes.stream().mapToDouble(Episode::depth).min().orElse(0.0);
        Point last = series.getLast();
        Current current = open == null
                ? new Current(0.0, last.date(), 0, deepestPast, false)
                : new Current(last.drawdown(), open.peak(), ChronoUnit.DAYS.between(open.peak(), last.date()),
                        deepestPast, last.drawdown() < deepestPast);
        if (open != null) {
            episodes.add(open);
        }
        episodes.sort(Comparator.comparingDouble(Episode::depth).thenComparing(Episode::peak));
        return new Result(series, episodes, current);
    }
}
