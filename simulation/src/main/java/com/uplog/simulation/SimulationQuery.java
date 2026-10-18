package com.uplog.simulation;

import com.uplog.core.CurrencyCode;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 검증·정규화한 시뮬레이션 요청. 같은 뜻의 요청은 같은 값이 되므로 그대로 캐시 키로 쓴다.
 * (종목코드 대문자·중복 제거, 열거값 대소문자 무시, 주기에 안 맞는 dayOfWeek/dayOfMonth는 버림)
 *
 * @param currency 결과 금액을 나타낼 기준 통화. 매수(원화 금액·날짜)는 같고, USD면 그날 환율로 달러로 환산해 다시 계산한다.
 */
public record SimulationQuery(
        List<String> etfs,
        SimulationPeriod period,
        RecurringSchedule schedule,
        AmountType amountType,
        long amount,
        CurrencyCode currency) {

    public static final int MAX_ETFS = 3;
    public static final long MIN_AMOUNT = 1_000;
    public static final long MAX_AMOUNT = 1_000_000_000;

    /** 토스 종목 심볼 문자(영문·숫자·'.'·'-'), 1–12자. 국내는 6자리 코드, 미국은 티커(예: BRK.B). */
    private static final Pattern CODE = Pattern.compile("[A-Z0-9.\\-]{1,12}");

    public SimulationQuery {
        etfs = List.copyOf(etfs);
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(schedule, "schedule");
        Objects.requireNonNull(amountType, "amountType");
        Objects.requireNonNull(currency, "currency");
    }

    /** 원화 기준 요청. */
    public SimulationQuery(
            List<String> etfs, SimulationPeriod period, RecurringSchedule schedule, AmountType amountType, long amount) {
        this(etfs, period, schedule, amountType, amount, CurrencyCode.KRW);
    }

    /** 원화 기준으로 쿼리 문자열을 검증한다. */
    public static SimulationQuery parse(
            String etfs,
            String period,
            String frequency,
            String dayOfWeek,
            String dayOfMonth,
            String amountType,
            String amount) {
        return parse(etfs, period, frequency, dayOfWeek, dayOfMonth, amountType, amount, null);
    }

    /** 쿼리 문자열 그대로 받아 검증한다. 실패하면 INVALID_PARAMETER. {@code currency}가 비면 KRW. */
    public static SimulationQuery parse(
            String etfs,
            String period,
            String frequency,
            String dayOfWeek,
            String dayOfMonth,
            String amountType,
            String amount,
            String currency) {
        return new SimulationQuery(
                parseEtfs(etfs),
                SimulationPeriod.fromCode(strip(period))
                        .orElseThrow(() -> SimulationException.invalid("period는 6M, 1Y, 3Y 중 하나여야 합니다.")),
                parseSchedule(frequency, dayOfWeek, dayOfMonth),
                parseEnum(AmountType.class, amountType, "amountType은 TOTAL 또는 PER_PURCHASE여야 합니다."),
                parseAmount(amount),
                blank(currency)
                        ? CurrencyCode.KRW
                        : parseEnum(CurrencyCode.class, currency, "currency는 KRW 또는 USD여야 합니다."));
    }

    private static List<String> parseEtfs(String raw) {
        if (blank(raw)) {
            throw SimulationException.invalid("etfs에 종목코드를 1–3개 넣어 주세요.");
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String part : raw.split(",", -1)) {
            String code = part.strip().toUpperCase(Locale.ROOT);
            if (!CODE.matcher(code).matches()) {
                throw SimulationException.invalid(
                        "종목코드 형식이 잘못됐습니다(영문·숫자·'.'·'-' 1–12자): '" + part.strip() + "'");
            }
            codes.add(code);
        }
        if (codes.size() > MAX_ETFS) {
            throw SimulationException.invalid("종목은 최대 " + MAX_ETFS + "개까지 비교할 수 있습니다.");
        }
        return List.copyOf(codes);
    }

    private static RecurringSchedule parseSchedule(String frequency, String dayOfWeek, String dayOfMonth) {
        Frequency f = parseEnum(Frequency.class, frequency, "frequency는 DAILY, WEEKLY, MONTHLY 중 하나여야 합니다.");
        return switch (f) {
            case DAILY -> RecurringSchedule.daily();
            case WEEKLY -> RecurringSchedule.weekly(parseDayOfWeek(dayOfWeek));
            case MONTHLY -> RecurringSchedule.monthly(parseDayOfMonth(dayOfMonth));
        };
    }

    private static DayOfWeek parseDayOfWeek(String raw) {
        String message = "WEEKLY 적립에는 dayOfWeek(MONDAY–FRIDAY)가 필요합니다.";
        DayOfWeek day = parseEnum(DayOfWeek.class, raw, message);
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            throw SimulationException.invalid(message);
        }
        return day;
    }

    private static int parseDayOfMonth(String raw) {
        String message = "MONTHLY 적립에는 dayOfMonth(1–31)가 필요합니다.";
        try {
            int day = Integer.parseInt(strip(raw));
            if (day < 1 || day > 31) {
                throw SimulationException.invalid(message);
            }
            return day;
        } catch (NumberFormatException e) {
            throw SimulationException.invalid(message);
        }
    }

    private static long parseAmount(String raw) {
        String message = "amount는 1,000원 이상 1,000,000,000원 이하의 정수여야 합니다.";
        try {
            long amount = Long.parseLong(strip(raw));
            if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
                throw SimulationException.invalid(message);
            }
            return amount;
        } catch (NumberFormatException e) {
            throw SimulationException.invalid(message);
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String message) {
        String value = strip(raw).toUpperCase(Locale.ROOT);
        return Arrays.stream(type.getEnumConstants())
                .filter(e -> e.name().equals(value))
                .findFirst()
                .orElseThrow(() -> SimulationException.invalid(message));
    }

    private static String strip(String s) {
        return s == null ? "" : s.strip();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
