package com.uplog.portfolio;

import com.uplog.calc.portfolio.SaleShadow;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/** 쿼리 파라미터 검증. 실패하면 INVALID_PARAMETER와 사용자에게 보여 줄 한국어 문장. */
final class PortfolioParams {

    static final List<String> BENCHMARKS = List.of("SPX", "KOSPI200");
    static final List<String> PERIODS = List.of("1M", "3M", "YTD", "1Y", "ALL");

    private PortfolioParams() {}

    /** 비면 null(= 마지막 기록일의 연도). */
    static Integer year(String raw, List<Integer> available) {
        if (blank(raw)) {
            return null;
        }
        String message = "year는 " + join(available) + " 중 하나여야 합니다.";
        try {
            int year = Integer.parseInt(raw.strip());
            if (!available.contains(year)) {
                throw PortfolioException.invalid(message);
            }
            return year;
        } catch (NumberFormatException e) {
            throw PortfolioException.invalid(message);
        }
    }

    static int horizon(String raw) {
        if (blank(raw)) {
            return 20;
        }
        String message = "horizon은 5, 20, 60 중 하나여야 합니다.";
        try {
            int horizon = Integer.parseInt(raw.strip());
            if (!SaleShadow.HORIZONS.contains(horizon)) {
                throw PortfolioException.invalid(message);
            }
            return horizon;
        } catch (NumberFormatException e) {
            throw PortfolioException.invalid(message);
        }
    }

    static String benchmark(String raw) {
        return oneOf(raw, "SPX", BENCHMARKS, "benchmark는 SPX 또는 KOSPI200이어야 합니다.");
    }

    static String period(String raw) {
        return oneOf(raw, "YTD", PERIODS, "period는 1M, 3M, YTD, 1Y, ALL 중 하나여야 합니다.");
    }

    static LocalDate date(String raw) {
        try {
            return LocalDate.parse(raw == null ? "" : raw.strip());
        } catch (DateTimeParseException e) {
            throw PortfolioException.invalid("날짜는 YYYY-MM-DD 형식이어야 합니다: '" + raw + "'");
        }
    }

    static String code(String raw) {
        return raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT);
    }

    private static String oneOf(String raw, String fallback, List<String> allowed, String message) {
        if (blank(raw)) {
            return fallback;
        }
        String value = raw.strip().toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) {
            throw PortfolioException.invalid(message);
        }
        return value;
    }

    private static String join(List<Integer> values) {
        return String.join(", ", values.stream().map(String::valueOf).toList());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
