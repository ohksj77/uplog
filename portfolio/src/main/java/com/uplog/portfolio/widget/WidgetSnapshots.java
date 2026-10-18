package com.uplog.portfolio.widget;

import com.uplog.portfolio.DaySource;
import com.uplog.portfolio.api.PortfolioDtos.LogDay;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 로그 칸 → 위젯 스냅샷. 스프링 없는 순수 함수(진하기 규칙은 web의 log-grid/logic.ts·portfolio-home/log-cells.ts와 같다). */
public final class WidgetSnapshots {

    /** 큰 위젯이 그리는 주 수. 작은·중간 위젯은 끝에서 잘라 쓴다. */
    public static final int WEEKS = 26;

    private WidgetSnapshots() {}

    /**
     * @param days 계좌 로그 칸(날짜 오름차순, 적어도 지난 1년 + {@link #WEEKS}주)
     * @param today 오늘(KST). 마지막 열은 오늘이 든 주이고, 오늘 뒤 칸은 null
     */
    public static WidgetSnapshot build(String accountName, boolean sample, List<LogDay> days, LocalDate today, Instant now) {
        List<LogDay> recorded = days.stream().filter(d -> !DaySource.MISSING.name().equals(d.source())).toList();
        LogDay last = recorded.isEmpty() ? null : recorded.getLast();
        LocalDate asOf = last == null ? null : last.date();

        long[] thresholds = asOf == null
                ? new long[] {0, 0, 0}
                : quartileThresholds(recorded.stream().filter(d -> d.date().isAfter(asOf.minusYears(1))).map(LogDay::pnl).toList());

        WidgetSnapshot.Today todayPnl = last == null ? null : new WidgetSnapshot.Today(last.pnl(), last.dailyReturn());
        return new WidgetSnapshot(WidgetSnapshot.VERSION, now, asOf, accountName, sample, todayPnl,
                tally(recorded, asOf, true), tally(recorded, asOf, false), streak(recorded), weeks(recorded, today, thresholds),
                Arrays.stream(thresholds).boxed().toList());
    }

    /** −4(가장 큰 손실) … 0(손익 0) … 4(가장 큰 이익). 경계 [a, b, c]: |v| < a → 1, < b → 2, < c → 3, 나머지 4. */
    public static int level(long pnl, long[] thresholds) {
        if (pnl == 0) {
            return 0;
        }
        long abs = Math.abs(pnl);
        int step = abs < thresholds[0] ? 1 : abs < thresholds[1] ? 2 : abs < thresholds[2] ? 3 : 4;
        return pnl > 0 ? step : -step;
    }

    /** 0이 아닌 값의 |v| 사분위수(25·50·75%, 선형 보간)를 {@link #niceWon}으로 맞춘다. 값이 없으면 [0, 0, 0]. */
    public static long[] quartileThresholds(List<Long> values) {
        double[] abs = values.stream().filter(v -> v != 0).mapToDouble(v -> Math.abs((double) v)).sorted().toArray();
        if (abs.length == 0) {
            return new long[] {0, 0, 0};
        }
        return new long[] {niceWon(quantile(abs, 0.25)), niceWon(quantile(abs, 0.5)), niceWon(quantile(abs, 0.75))};
    }

    private static double quantile(double[] sorted, double p) {
        double pos = (sorted.length - 1) * p;
        int lo = (int) Math.floor(pos);
        int hi = Math.min(sorted.length - 1, lo + 1);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
    }

    /** 경계를 읽기 쉬운 값으로: 1만 원 이상은 만 원 단위, 1천 원 이상은 천 원 단위(web의 niceWon과 같다, 반올림은 JS Math.round). */
    public static long niceWon(double v) {
        if (v >= 10_000) {
            return Math.round(v / 10_000) * 10_000;
        }
        if (v >= 1_000) {
            return Math.round(v / 1_000) * 1_000;
        }
        return Math.round(v);
    }

    /** asOf가 든 달(month) 또는 해의 손익 합·번 날·잃은 날. */
    private static WidgetSnapshot.Tally tally(List<LogDay> recorded, LocalDate asOf, boolean month) {
        if (asOf == null) {
            return new WidgetSnapshot.Tally(0, 0, 0);
        }
        long pnl = 0;
        int gain = 0;
        int loss = 0;
        for (LogDay d : recorded) {
            if (d.date().getYear() != asOf.getYear() || month && d.date().getMonth() != asOf.getMonth()) {
                continue;
            }
            pnl += d.pnl();
            if (d.pnl() > 0) {
                gain++;
            } else if (d.pnl() < 0) {
                loss++;
            }
        }
        return new WidgetSnapshot.Tally(pnl, gain, loss);
    }

    /** 기록한 날만 이어서 센다(MISSING은 건너뛴다). 손익 0인 날에서 끊긴다. */
    private static WidgetSnapshot.Streak streak(List<LogDay> recorded) {
        if (recorded.isEmpty() || recorded.getLast().pnl() == 0) {
            return new WidgetSnapshot.Streak(null, 0);
        }
        int sign = Long.signum(recorded.getLast().pnl());
        int length = 0;
        for (int i = recorded.size() - 1; i >= 0 && Long.signum(recorded.get(i).pnl()) == sign; i--) {
            length++;
        }
        return new WidgetSnapshot.Streak(sign > 0 ? "GAIN" : "LOSS", length);
    }

    private static List<WidgetSnapshot.Week> weeks(List<LogDay> recorded, LocalDate today, long[] thresholds) {
        Map<LocalDate, LogDay> byDate = new HashMap<>();
        recorded.forEach(d -> byDate.put(d.date(), d));
        LocalDate lastMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<WidgetSnapshot.Week> weeks = new ArrayList<>(WEEKS);
        for (int w = WEEKS - 1; w >= 0; w--) {
            LocalDate monday = lastMonday.minusWeeks(w);
            List<WidgetSnapshot.Day> cells = new ArrayList<>(5);
            for (int i = 0; i < 5; i++) {
                LocalDate date = monday.plusDays(i);
                LogDay d = date.isAfter(today) ? null : byDate.get(date);
                cells.add(d == null
                        ? new WidgetSnapshot.Day(date, null, null)
                        : new WidgetSnapshot.Day(date, level(d.pnl(), thresholds), d.pnl()));
            }
            weeks.add(new WidgetSnapshot.Week(monday, cells));
        }
        return weeks;
    }
}
