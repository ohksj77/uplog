package com.uplog.calc.portfolio;

import com.uplog.calc.Trade;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

/**
 * 매매 습관: 처분 효과(이익 포지션과 손실 포지션의 보유 기간), 물타기, 월별 매매 횟수와 회전율.
 */
public final class TradingHabits {

    /** 손실 보유 일수가 이익 보유 일수의 이 배 이상이면 HOLD_LOSERS, 1/이 배 이하면 CUT_LOSSES. */
    public static final double RATIO_THRESHOLD = 1.5;

    /** 한쪽이 이 수보다 적으면 판정하지 않는다. */
    public static final int MIN_PER_SIDE = 2;

    private TradingHabits() {}

    public enum Verdict {
        HOLD_LOSERS,
        CUT_LOSSES,
        BALANCED,
        INSUFFICIENT
    }

    /** 한쪽(이익 또는 손실) 포지션 묶음. count가 0이면 나머지는 0. 일수는 달력일. */
    public record Side(int count, double avgDays, double medianDays, double avgReturn) {}

    /** @param ratio losers.avgDays / winners.avgDays. winners가 없거나 평균 보유 일수가 0이면 empty. */
    public record Holding(Side winners, Side losers, Optional<Double> ratio, Verdict verdict) {}

    /**
     * 물타기 체결 한 건.
     *
     * @param day 계좌 거래일(체결일이 휴장일이면 다음 거래일)
     * @param avgCostLocal 체결 직전 평균단가(현지)
     * @param belowAvg 체결가 / 직전 평균단가 − 1(음수)
     * @param period 이 체결이 속한 포지션
     * @param periodIndex {@link HoldingPeriods#of}가 낸 목록에서의 위치
     */
    public record AveragingDownBuy(
            Execution execution, LocalDate day, BigDecimal avgCostLocal, double belowAvg, HoldingPeriods.Period period,
            int periodIndex) {}

    /**
     * @param totalBuys 전체 매수 체결 수
     * @param positions 물타기가 한 번이라도 있었던 포지션 수 / positionsWon 그중 수익률 > 0
     * @param items 최신 순
     */
    public record AveragingDown(int totalBuys, int positions, int positionsWon, List<AveragingDownBuy> items) {

        public AveragingDown {
            items = List.copyOf(items);
        }

        public double shareOfBuys() {
            return totalBuys == 0 ? 0.0 : (double) items.size() / totalBuys;
        }
    }

    /** @param turnover 그달 매도 금액 / 그달 기록일 평균 평가금(평가금이 없으면 0) */
    public record Activity(YearMonth month, int buys, int sells, double turnover) {}

    /** 끝난 포지션만. 이익은 returnRate > 0, 손실은 ≤ 0. */
    public static Holding holding(List<HoldingPeriods.Period> periods) {
        List<HoldingPeriods.Period> closed = periods.stream().filter(p -> !p.isOpen()).toList();
        List<HoldingPeriods.Period> winners = closed.stream().filter(p -> p.returnRate() > 0).toList();
        List<HoldingPeriods.Period> losers = closed.stream().filter(p -> p.returnRate() <= 0).toList();
        Side w = side(winners);
        Side l = side(losers);
        long winDays = winners.stream().mapToLong(HoldingPeriods.Period::days).sum();
        long loseDays = losers.stream().mapToLong(HoldingPeriods.Period::days).sum();
        Optional<Double> ratio = winDays == 0 ? Optional.empty() : Optional.of(l.avgDays() / w.avgDays());

        Verdict verdict;
        if (winners.size() < MIN_PER_SIDE || losers.size() < MIN_PER_SIDE) {
            verdict = Verdict.INSUFFICIENT;
        } else {
            // 경계(정확히 1.5배)가 부동소수 오차에 흔들리지 않도록 정수로 비교한다:
            // ratio = (loseDays / nL) / (winDays / nW), 1.5 = 3/2.
            long lhs = 2L * loseDays * winners.size();
            long rhs = 3L * winDays * losers.size();
            long lhsCut = 3L * loseDays * winners.size();
            long rhsCut = 2L * winDays * losers.size();
            if (lhs >= rhs) {
                verdict = winDays == 0 && loseDays == 0 ? Verdict.BALANCED : Verdict.HOLD_LOSERS;
            } else if (lhsCut <= rhsCut) {
                verdict = Verdict.CUT_LOSSES;
            } else {
                verdict = Verdict.BALANCED;
            }
        }
        return new Holding(w, l, ratio, verdict);
    }

    private static Side side(List<HoldingPeriods.Period> periods) {
        int n = periods.size();
        if (n == 0) {
            return new Side(0, 0.0, 0.0, 0.0);
        }
        long[] days = periods.stream().mapToLong(HoldingPeriods.Period::days).sorted().toArray();
        double median = n % 2 == 1 ? days[n / 2] : (days[n / 2 - 1] + days[n / 2]) / 2.0;
        double avgDays = (double) Arrays.stream(days).sum() / n;
        double avgReturn = periods.stream().mapToDouble(HoldingPeriods.Period::returnRate).average().orElse(0.0);
        return new Side(n, avgDays, median, avgReturn);
    }

    /**
     * 물타기: 이미 보유 중인 종목을 그 순간 이동평균 단가(현지 통화)보다 낮은 가격에 추가 매수한 체결.
     * 같은 날 체결은 리플레이와 같이 입력 순서대로 적용한다.
     *
     * @param periods {@link HoldingPeriods#of}(history)
     */
    public static AveragingDown averagingDown(PortfolioHistory history, List<HoldingPeriods.Period> periods) {
        Map<String, Position> positions = new HashMap<>();
        List<AveragingDownBuy> found = new ArrayList<>();
        int totalBuys = 0;
        for (PortfolioDay day : history.days()) {
            for (Execution e : day.executions()) {
                Position p = positions.getOrDefault(e.instrument(), Position.EMPTY);
                if (e.side() == Trade.Side.SELL) {
                    positions.put(e.instrument(), p.sell(e.quantity()));
                    continue;
                }
                totalBuys++;
                if (p.isOpen() && e.priceLocal().compareTo(p.avgCostLocal()) < 0) {
                    BigDecimal avg = p.avgCostLocal();
                    double below = e.priceLocal().divide(avg, MathContext.DECIMAL64).subtract(BigDecimal.ONE).doubleValue();
                    int index = periodIndex(periods, e.instrument(), day.date());
                    found.add(new AveragingDownBuy(e, day.date(), avg, below, periods.get(index), index));
                }
                positions.put(e.instrument(), p.buy(e.quantity(), e.grossLocal(), e.grossKrw()));
            }
        }
        TreeSet<Integer> touched = new TreeSet<>();
        found.forEach(b -> touched.add(b.periodIndex()));
        int won = (int) touched.stream().filter(i -> periods.get(i).returnRate() > 0).count();
        return new AveragingDown(totalBuys, touched.size(), won, found.reversed());
    }

    /** 그 종목에서 day에 열려 있던(day에 닫힌 것 포함) 가장 늦게 연 포지션. */
    private static int periodIndex(List<HoldingPeriods.Period> periods, String instrument, LocalDate day) {
        int found = -1;
        for (int i = 0; i < periods.size(); i++) {
            HoldingPeriods.Period p = periods.get(i);
            if (p.instrument().equals(instrument) && !p.open().isAfter(day)
                    && p.close().map(c -> !c.isBefore(day)).orElse(true)) {
                found = i;
            }
        }
        if (found < 0) {
            throw new IllegalStateException("체결이 속한 포지션이 없습니다: " + instrument + " " + day);
        }
        return found;
    }

    /**
     * 첫 기록 달부터 마지막 달까지 빠짐없이. 기록일(MISSING 제외)만 쓴다: 수집 실패일의 체결은 {@link AccountLog}가
     * 다음 기록일에 몰아 넣은 대로 센다.
     *
     * @param log {@link AccountLog#of}의 전체 로그, 날짜순
     */
    public static List<Activity> activity(List<AccountLog.Entry> log) {
        if (log.isEmpty()) {
            return List.of();
        }
        Map<YearMonth, long[]> counts = new LinkedHashMap<>(); // buys, sells
        Map<YearMonth, BigDecimal> sold = new HashMap<>();
        Map<YearMonth, BigDecimal> valueSum = new HashMap<>();
        Map<YearMonth, Integer> valueDays = new HashMap<>();
        for (AccountLog.Entry e : log) {
            if (e.missing()) {
                continue;
            }
            YearMonth ym = YearMonth.from(e.date());
            long[] c = counts.computeIfAbsent(ym, k -> new long[2]);
            for (Execution x : e.executions()) {
                if (x.side() == Trade.Side.BUY) {
                    c[0]++;
                } else {
                    c[1]++;
                    sold.merge(ym, x.grossKrw(), BigDecimal::add);
                }
            }
            valueSum.merge(ym, e.value(), BigDecimal::add);
            valueDays.merge(ym, 1, Integer::sum);
        }
        List<Activity> result = new ArrayList<>();
        YearMonth last = YearMonth.from(log.getLast().date());
        for (YearMonth ym = YearMonth.from(log.getFirst().date()); !ym.isAfter(last); ym = ym.plusMonths(1)) {
            long[] c = counts.getOrDefault(ym, new long[2]);
            BigDecimal sum = valueSum.get(ym);
            double turnover = 0.0;
            if (sum != null && sum.signum() > 0) {
                BigDecimal avg = sum.divide(BigDecimal.valueOf(valueDays.get(ym)), MathContext.DECIMAL64);
                turnover = sold.getOrDefault(ym, BigDecimal.ZERO).divide(avg, MathContext.DECIMAL64).doubleValue();
            }
            result.add(new Activity(ym, (int) c[0], (int) c[1], turnover));
        }
        return List.copyOf(result);
    }
}
