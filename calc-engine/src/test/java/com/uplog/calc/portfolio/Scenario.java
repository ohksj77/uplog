package com.uplog.calc.portfolio;

import com.uplog.calc.PriceSeries;
import com.uplog.calc.Trade;
import com.uplog.core.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 손으로 검산할 수 있는 작은 계좌. 2026-10-12(월)–15(목), 원화 종목 KR1과 달러 종목 US1.
 *
 * <pre>
 *            KR1   US1(USD)  USD/KRW
 * 월 MON     100   10        1000   10만 원 입금, KR1 10주 @100(수수료 1), US1 5주 @10(수수료 0.1달러)
 * 화 TUE     110   11        1000
 * 수 WED      99   11        1100   KR1 10주 @99 매도(수수료 1, 세금 2), US1 배당 300원
 * 목 THU      99   12        1100
 * </pre>
 *
 * 평가금: 월 99,899 / 화 104,999 / 수 110,686 / 목 116,186.
 */
final class Scenario {

    static final LocalDate MON = LocalDate.of(2026, 10, 12);
    static final LocalDate TUE = MON.plusDays(1);
    static final LocalDate WED = MON.plusDays(2);
    static final LocalDate THU = MON.plusDays(3);
    static final List<LocalDate> DAYS = List.of(MON, TUE, WED, THU);

    static final Map<String, CurrencyCode> CURRENCIES = Map.of("KR1", CurrencyCode.KRW, "US1", CurrencyCode.USD);

    private Scenario() {}

    static PortfolioReplay.Input input() {
        return new PortfolioReplay.Input(
                DAYS,
                CURRENCIES,
                Map.of("KR1", series("100", "110", "99", "99"), "US1", series("10", "11", "11", "12")),
                series("1000", "1000", "1100", "1100"),
                List.of(
                        buy(MON, "KR1", 10, "100", "1", "1", "0"),
                        buy(MON, "US1", 5, "10", "1000", "0.1", "0"),
                        sell(WED, "KR1", 10, "99", "1", "1", "2")),
                List.of(deposit(MON, 100_000)),
                List.of(new Dividend(WED, "US1", d("300"))));
    }

    static PortfolioHistory history() {
        return PortfolioReplay.replay(input());
    }

    /** MON부터 평일마다 종가를 배정한다. */
    static PriceSeries series(String... closes) {
        Map<LocalDate, BigDecimal> map = new LinkedHashMap<>();
        LocalDate day = MON;
        for (String close : closes) {
            while (day.getDayOfWeek().getValue() > 5) {
                day = day.plusDays(1);
            }
            map.put(day, d(close));
            day = day.plusDays(1);
        }
        return new PriceSeries(map);
    }

    static List<LocalDate> weekdays(int count) {
        List<LocalDate> days = new ArrayList<>();
        for (LocalDate day = MON; days.size() < count; day = day.plusDays(1)) {
            if (day.getDayOfWeek().getValue() <= 5) {
                days.add(day);
            }
        }
        return days;
    }

    static Execution buy(LocalDate date, String code, long qty, String price, String fx, String fee, String tax) {
        return new Execution(date, code, Trade.Side.BUY, BigDecimal.valueOf(qty), d(price), d(fx), d(fee), d(tax));
    }

    static Execution sell(LocalDate date, String code, long qty, String price, String fx, String fee, String tax) {
        return new Execution(date, code, Trade.Side.SELL, BigDecimal.valueOf(qty), d(price), d(fx), d(fee), d(tax));
    }

    static AccountFlow deposit(LocalDate date, long amount) {
        return new AccountFlow(date, AccountFlow.Type.DEPOSIT, BigDecimal.valueOf(amount));
    }

    static BigDecimal d(String value) {
        return new BigDecimal(value);
    }
}
