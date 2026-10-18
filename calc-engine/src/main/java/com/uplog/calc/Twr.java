package com.uplog.calc;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** 시간가중수익률. r_t = (V_t − CF_t) / V_{t−1} − 1, TWR = Π(1 + r_t) − 1. */
public final class Twr {

    private Twr() {}

    /**
     * 날짜순으로 정렬된 평가 시계열에서 일별 수익률과 누적 지수를 구한다.
     * 첫날과, 전날 평가금액이 0인 날(전액 출금 후 재입금 등)의 수익률은 0으로 둔다.
     */
    public static List<DailyReturn> compute(List<DailyValuation> valuations) {
        List<DailyReturn> result = new ArrayList<>(valuations.size());
        double index = 1.0;
        DailyValuation prev = null;
        for (DailyValuation v : valuations) {
            if (prev != null && !v.date().isAfter(prev.date())) {
                throw new IllegalArgumentException("날짜가 오름차순이 아닙니다: " + prev.date() + " → " + v.date());
            }
            double r = 0.0;
            if (prev != null && prev.value().signum() > 0) {
                r = v.value().subtract(v.netCashFlow())
                        .divide(prev.value(), MathContext.DECIMAL64)
                        .subtract(BigDecimal.ONE)
                        .doubleValue();
            }
            index *= 1.0 + r;
            result.add(new DailyReturn(v.date(), r, index));
            prev = v;
        }
        return List.copyOf(result);
    }

    /** 두 지점 사이의 구간 수익률. */
    public static double between(DailyReturn start, DailyReturn end) {
        return end.twrIndex() / start.twrIndex() - 1.0;
    }

    /** 연환산 수익률: index^(365 / 경과일) − 1. 경과일이 0이면 0. */
    public static double annualize(double twrIndex, LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to);
        if (days <= 0) {
            return 0.0;
        }
        return Math.pow(twrIndex, 365.0 / days) - 1.0;
    }
}
