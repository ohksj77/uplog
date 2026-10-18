package com.uplog.calc;

import java.time.LocalDate;

/**
 * @param dailyReturn 현금흐름을 제거한 그날 수익률
 * @param twrIndex 첫날을 1.0으로 둔 누적 시간가중수익률 지수
 */
public record DailyReturn(LocalDate date, double dailyReturn, double twrIndex) {

    public double cumulativeReturn() {
        return twrIndex - 1.0;
    }
}
