package com.uplog.calc.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 자산 구성 변화: 시기별로 돈이 어느 종목·국내·해외·현금에 있었나.
 * 주마다 마지막 기록일(+ 마지막 날)을 점으로 쓰고, 기간 평균 평가금이 큰 종목 몇 개만 따로, 나머지는 "그 외"로 묶는다.
 */
public final class AllocationHistory {

    private AllocationHistory() {}

    public enum Kind {
        INSTRUMENT,
        OTHER,
        CASH
    }

    /** @param key 종목 코드, "OTHER", "CASH" */
    public record Group(String key, Kind kind) {}

    /** @param values groups 순서의 평가금(원). 합 = total, kr + us + cash = total */
    public record Point(LocalDate date, BigDecimal total, List<BigDecimal> values, BigDecimal kr, BigDecimal us, BigDecimal cash) {}

    public record Peak(String key, double weight, LocalDate date) {}

    /** @param peak 보유한 적이 없으면 null */
    public record Result(List<Group> groups, List<Point> points, Peak peak) {}

    public static final String OTHER = "OTHER";
    public static final String CASH = "CASH";

    /**
     * @param observed 기록이 있는 날(MISSING 제외)
     * @param overseas 해외(US) 종목 코드
     * @param top 따로 보여 줄 종목 수
     */
    public static Result of(PortfolioHistory history, Set<LocalDate> observed, Set<String> overseas, int top) {
        List<PortfolioDay> days = sample(history.days().stream().filter(d -> observed.contains(d.date())).toList());

        // 기간 평균 평가금이 큰 종목 순
        Map<String, BigDecimal> sum = new HashMap<>();
        for (PortfolioDay d : days) {
            d.instruments().forEach((code, i) -> {
                if (i.value().signum() > 0) {
                    sum.merge(code, i.value(), BigDecimal::add);
                }
            });
        }
        List<String> ranked = sum.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
        List<String> shown = ranked.subList(0, Math.min(top, ranked.size()));
        boolean hasOther = ranked.size() > shown.size();

        List<Group> groups = new ArrayList<>();
        shown.forEach(code -> groups.add(new Group(code, Kind.INSTRUMENT)));
        if (hasOther) {
            groups.add(new Group(OTHER, Kind.OTHER));
        }
        groups.add(new Group(CASH, Kind.CASH));

        List<Point> points = new ArrayList<>();
        Peak peak = null;
        for (PortfolioDay d : days) {
            Map<String, BigDecimal> values = new LinkedHashMap<>();
            groups.forEach(g -> values.put(g.key(), BigDecimal.ZERO));
            BigDecimal kr = BigDecimal.ZERO;
            BigDecimal us = BigDecimal.ZERO;
            for (var e : d.instruments().entrySet()) {
                BigDecimal v = e.getValue().value();
                if (v.signum() <= 0) {
                    continue;
                }
                values.merge(shown.contains(e.getKey()) ? e.getKey() : OTHER, v, BigDecimal::add);
                if (overseas.contains(e.getKey())) {
                    us = us.add(v);
                } else {
                    kr = kr.add(v);
                }
            }
            // 역산 추정 구간에서 장부 현금이 음수가 될 수 있다. 구성 그래프에선 0으로 둔다.
            BigDecimal cash = d.cash().max(BigDecimal.ZERO);
            values.put(CASH, cash);
            BigDecimal total = kr.add(us).add(cash);
            points.add(new Point(d.date(), total, List.copyOf(values.values()), kr, us, cash));

            if (total.signum() > 0) {
                for (var e : d.instruments().entrySet()) {
                    double w = e.getValue().value().doubleValue() / total.doubleValue();
                    if (e.getValue().value().signum() > 0 && (peak == null || w > peak.weight())) {
                        peak = new Peak(e.getKey(), w, d.date());
                    }
                }
            }
        }
        return new Result(List.copyOf(groups), List.copyOf(points), peak);
    }

    /** 주마다 마지막 날 + 마지막 날. */
    static List<PortfolioDay> sample(List<PortfolioDay> days) {
        Map<String, PortfolioDay> lastOfWeek = new LinkedHashMap<>();
        for (PortfolioDay d : days) {
            String week = d.date().get(IsoFields.WEEK_BASED_YEAR) + "-" + d.date().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            lastOfWeek.put(week, d);
        }
        List<PortfolioDay> out = new ArrayList<>(lastOfWeek.values());
        if (!days.isEmpty() && !out.getLast().equals(days.getLast())) {
            out.add(days.getLast());
        }
        out.sort(Comparator.comparing(PortfolioDay::date));
        return out;
    }
}
