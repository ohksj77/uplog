package com.uplog.calc.portfolio;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 월별 성적표. 기록일(MISSING 제외)만 쓴다: 수집 실패일의 손익은 {@link AccountLog}가 다음 기록일에 몰아 넣으므로
 * 그날이 속한 달로 간다. 월 수익률은 그달 일별 수익률의 곱 − 1(TWR), 손익은 원 단위로 반올림한 하루 손익의 합이라
 * 연 합계가 수익 로그의 totalPnl과 정확히 같다.
 */
public final class MonthlyReturns {

    private MonthlyReturns() {}

    /** @param days 그달 기록일 수. 0이면 기록 없는 달이고 나머지 값은 0이다. */
    public record Month(YearMonth month, int days, double returnRate, long pnl, long netCashFlow) {

        public boolean recorded() {
            return days > 0;
        }
    }

    /** @param months 항상 1–12월 12개 / returnRate 그해 TWR / pnl 그해 손익 합 */
    public record Year(int year, List<Month> months, double returnRate, long pnl) {

        public Year {
            months = List.copyOf(months);
        }
    }

    /** @param best·worst 기록 있는 달 중 수익률 최고·최저(같으면 이른 달) */
    public record Result(List<Year> years, Optional<Month> best, Optional<Month> worst, int positiveMonths, int negativeMonths) {

        public Result {
            years = List.copyOf(years);
        }
    }

    /** @param log {@link AccountLog#of}의 전체 로그(MISSING 포함), 날짜순. 연도는 로그에 나오는 해, 오래된 순. */
    public static Result of(List<AccountLog.Entry> log) {
        List<Year> years = new ArrayList<>();
        Month best = null;
        Month worst = null;
        int positive = 0;
        int negative = 0;
        List<Integer> yearList = log.stream().map(e -> e.date().getYear()).distinct().sorted().toList();
        for (int year : yearList) {
            List<Month> months = new ArrayList<>(12);
            double yearGrowth = 1.0;
            long yearPnl = 0;
            for (int m = 1; m <= 12; m++) {
                YearMonth ym = YearMonth.of(year, m);
                int days = 0;
                double growth = 1.0;
                long pnl = 0;
                long flows = 0;
                for (AccountLog.Entry e : log) {
                    if (e.missing() || !YearMonth.from(e.date()).equals(ym)) {
                        continue;
                    }
                    days++;
                    growth *= 1.0 + e.dailyReturn();
                    pnl += PnlWaterfall.won(e.pnl());
                    flows += PnlWaterfall.won(e.netCashFlow());
                }
                Month month = new Month(ym, days, days == 0 ? 0.0 : growth - 1.0, pnl, flows);
                months.add(month);
                yearGrowth *= growth;
                yearPnl += pnl;
                if (!month.recorded()) {
                    continue;
                }
                if (best == null || month.returnRate() > best.returnRate()) {
                    best = month;
                }
                if (worst == null || month.returnRate() < worst.returnRate()) {
                    worst = month;
                }
                if (month.returnRate() > 0) {
                    positive++;
                } else if (month.returnRate() < 0) {
                    negative++;
                }
            }
            years.add(new Year(year, months, yearGrowth - 1.0, yearPnl));
        }
        return new Result(years, Optional.ofNullable(best), Optional.ofNullable(worst), positive, negative);
    }
}
