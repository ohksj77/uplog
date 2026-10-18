package com.uplog.broker.toss.fake;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 가짜 토스 서버의 결정적 시세. 2023-01-02부터 평일마다 고정 시드 랜덤워크(휴장일 없음). 같은 날짜에는 늘 같은 값이 나온다.
 * 종목명·가격대만 실제 종목을 흉내 낸 합성 데이터다.
 */
final class FakeTossMarket {

    static final LocalDate EPOCH = LocalDate.of(2023, 1, 2);
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    record Symbol(String symbol, String name, String country, String currency, double start, double drift, double vol) {

        boolean usd() {
            return currency.equals("USD");
        }

        ZoneId zone() {
            return usd() ? NEW_YORK : SEOUL;
        }
    }

    static final List<Symbol> SYMBOLS = List.of(
            new Symbol("005930", "삼성전자", "KR", "KRW", 58_000, 0.0003, 0.017),
            new Symbol("000660", "SK하이닉스", "KR", "KRW", 76_000, 0.0012, 0.024),
            new Symbol("035420", "NAVER", "KR", "KRW", 180_000, 0.0000, 0.020),
            new Symbol("069500", "KODEX 200", "KR", "KRW", 30_000, 0.0003, 0.010),
            new Symbol("AAPL", "애플", "US", "USD", 125, 0.0006, 0.016),
            new Symbol("NVDA", "엔비디아", "US", "USD", 15, 0.0022, 0.030),
            new Symbol("TSLA", "테슬라", "US", "USD", 110, 0.0008, 0.035),
            new Symbol("SPY", "SPDR S&P 500 ETF", "US", "USD", 380, 0.0005, 0.009));

    static final Map<String, Symbol> BY_CODE = SYMBOLS.stream().collect(Collectors.toMap(Symbol::symbol, Function.identity()));

    private final LocalDate today;
    private final Map<String, NavigableMap<LocalDate, BigDecimal>> closes = new TreeMap<>();
    private final NavigableMap<LocalDate, BigDecimal> usdKrw = new TreeMap<>();

    FakeTossMarket(LocalDate today) {
        this.today = today;
        for (Symbol s : SYMBOLS) {
            Random rnd = new Random(s.symbol().hashCode() * 31L + 7);
            NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
            double price = s.start();
            for (LocalDate d = EPOCH; !d.isAfter(today); d = d.plusDays(1)) {
                if (!weekday(d)) {
                    continue;
                }
                price *= Math.exp(s.drift() + s.vol() * rnd.nextGaussian());
                series.put(d, BigDecimal.valueOf(price).setScale(s.usd() ? 2 : 0, RoundingMode.HALF_UP));
            }
            closes.put(s.symbol(), series);
        }
        Random rnd = new Random(1400);
        double fx = 1300;
        for (LocalDate d = EPOCH; !d.isAfter(today); d = d.plusDays(1)) {
            if (!weekday(d)) {
                continue;
            }
            fx *= Math.exp(0.004 * rnd.nextGaussian() + 0.01 * Math.log(1380 / fx));
            usdKrw.put(d, BigDecimal.valueOf(fx).setScale(2, RoundingMode.HALF_UP));
        }
    }

    LocalDate today() {
        return today;
    }

    static boolean weekday(LocalDate d) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    NavigableMap<LocalDate, BigDecimal> closes(String symbol) {
        return closes.get(symbol);
    }

    /** 그날(없으면 직전) 종가. */
    BigDecimal close(String symbol, LocalDate date) {
        return closes.get(symbol).floorEntry(date).getValue();
    }

    BigDecimal lastClose(String symbol) {
        return closes.get(symbol).lastEntry().getValue();
    }

    /** 그날(없으면 직전) 매매기준율. EPOCH 이전이면 empty. */
    Optional<BigDecimal> usdKrw(LocalDate date) {
        var e = usdKrw.floorEntry(date);
        return e == null ? Optional.empty() : Optional.of(e.getValue());
    }
}
