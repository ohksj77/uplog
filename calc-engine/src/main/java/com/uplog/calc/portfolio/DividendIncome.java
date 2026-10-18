package com.uplog.calc.portfolio;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 배당 흐름. 리플레이가 계좌에 넣은 배당(마지막 날 뒤의 배당은 빠진다)을 지급일의 달로 묶는다.
 * 금액은 건마다 원 단위로 반올림한 뒤 더하므로 월·연·종목별 합이 total과 정확히 같다.
 * 배당은 사건이라 수집 실패일(MISSING)과 상관없이 지급일에 센다.
 */
public final class DividendIncome {

    private DividendIncome() {}

    /** @param date 지급일 */
    public record Item(LocalDate date, String instrument, long amount) {}

    /** @param items 지급일 오름차순 */
    public record Month(YearMonth month, long amount, List<Item> items) {

        public Month {
            items = List.copyOf(items);
        }
    }

    public record Year(int year, long amount) {}

    public record ByInstrument(String instrument, long amount, int count) {}

    /**
     * @param months 첫 기록 달부터 마지막 달까지 빠짐없이, 오래된 순
     * @param byYear months가 걸친 해마다 하나(배당 없으면 0), 오래된 순
     * @param byInstrument 금액 큰 순(같으면 종목코드순)
     * @param trailing12m 마지막 날 기준 최근 12개월(마지막 날 − 12개월 초과 ~ 마지막 날) 합
     * @param trailingYield trailing12m / 마지막 평가금(0이면 0)
     */
    public record Result(
            List<Month> months, List<Year> byYear, List<ByInstrument> byInstrument, long total, long trailing12m,
            double trailingYield) {

        public Result {
            months = List.copyOf(months);
            byYear = List.copyOf(byYear);
            byInstrument = List.copyOf(byInstrument);
        }
    }

    /** @param lastValue 마지막 기록일의 총평가금(원) */
    public static Result of(PortfolioHistory history, BigDecimal lastValue) {
        List<Item> items = history.days().stream()
                .flatMap(d -> d.dividendEvents().stream())
                .map(d -> new Item(d.date(), d.instrument(), PnlWaterfall.won(d.amount())))
                .sorted(Comparator.comparing(Item::date).thenComparing(Item::instrument))
                .toList();
        LocalDate lastDate = history.last().date();
        YearMonth first = YearMonth.from(history.first().date());
        if (!items.isEmpty() && YearMonth.from(items.getFirst().date()).isBefore(first)) {
            first = YearMonth.from(items.getFirst().date()); // 첫 거래일 전에 지급된 배당
        }
        YearMonth last = YearMonth.from(lastDate);

        List<Month> months = new ArrayList<>();
        Map<Integer, Long> byYear = new TreeMap<>();
        for (YearMonth ym = first; !ym.isAfter(last); ym = ym.plusMonths(1)) {
            YearMonth m = ym;
            List<Item> in = items.stream().filter(i -> YearMonth.from(i.date()).equals(m)).toList();
            long amount = in.stream().mapToLong(Item::amount).sum();
            months.add(new Month(ym, amount, in));
            byYear.merge(ym.getYear(), amount, Long::sum);
        }

        Map<String, long[]> instruments = new TreeMap<>(); // amount, count
        for (Item i : items) {
            long[] acc = instruments.computeIfAbsent(i.instrument(), k -> new long[2]);
            acc[0] += i.amount();
            acc[1]++;
        }
        List<ByInstrument> byInstrument = new ArrayList<>();
        instruments.forEach((code, acc) -> byInstrument.add(new ByInstrument(code, acc[0], (int) acc[1])));
        byInstrument.sort(Comparator.comparingLong(ByInstrument::amount).reversed().thenComparing(ByInstrument::instrument));

        long total = items.stream().mapToLong(Item::amount).sum();
        LocalDate trailingFrom = lastDate.minusMonths(12);
        long trailing = items.stream().filter(i -> i.date().isAfter(trailingFrom)).mapToLong(Item::amount).sum();
        double yield = lastValue.signum() <= 0 ? 0.0
                : BigDecimal.valueOf(trailing).divide(lastValue, MathContext.DECIMAL64).doubleValue();
        return new Result(months, byYear.entrySet().stream().map(e -> new Year(e.getKey(), e.getValue())).toList(),
                byInstrument, total, trailing, yield);
    }
}
