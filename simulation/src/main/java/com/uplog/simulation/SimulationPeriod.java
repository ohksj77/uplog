package com.uplog.simulation;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

/** 종료일(시세 마지막 날)로부터 거슬러 올라가는 기간. */
public enum SimulationPeriod {
    SIX_MONTHS("6M"),
    ONE_YEAR("1Y"),
    THREE_YEARS("3Y");

    private final String code;

    SimulationPeriod(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public LocalDate startFrom(LocalDate to) {
        return switch (this) {
            case SIX_MONTHS -> to.minusMonths(6);
            case ONE_YEAR -> to.minusYears(1);
            case THREE_YEARS -> to.minusYears(3);
        };
    }

    public static Optional<SimulationPeriod> fromCode(String code) {
        return Arrays.stream(values()).filter(p -> p.code.equalsIgnoreCase(code)).findFirst();
    }
}
