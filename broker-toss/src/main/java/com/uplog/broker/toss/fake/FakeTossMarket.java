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

    /**
     * @param exchange 상장 시장(KOSPI, KOSDAQ, NYSE, NASDAQ, AMEX)
     * @param securityType 토스 종목 유형(STOCK, ETF, FOREIGN_ETF, ETN …)
     * @param listDate 상장일. 이보다 이른 일봉은 없다(EPOCH보다 늦을 때만 의미가 있다)
     */
    record Symbol(String symbol, String name, String country, String currency, double start, double drift, double vol,
            String exchange, String securityType, boolean commonShare, String isin, LocalDate listDate) {

        Symbol(String symbol, String name, String country, String currency, double start, double drift, double vol,
                String exchange, String securityType) {
            this(symbol, name, country, currency, start, drift, vol, exchange, securityType, true,
                    (country.equals("KR") ? "KR7" : "US0") + String.format("%-8s", symbol).replace(' ', '0') + "0", null);
        }

        boolean usd() {
            return currency.equals("USD");
        }

        ZoneId zone() {
            return usd() ? NEW_YORK : SEOUL;
        }
    }

    /** 가짜 계좌가 거래하는 종목. 순서·구성을 바꾸면 가짜 계좌의 체결이 달라진다. */
    static final List<Symbol> SYMBOLS = List.of(
            new Symbol("005930", "삼성전자", "KR", "KRW", 58_000, 0.0003, 0.017, "KOSPI", "STOCK"),
            new Symbol("000660", "SK하이닉스", "KR", "KRW", 76_000, 0.0012, 0.024, "KOSPI", "STOCK"),
            new Symbol("035420", "NAVER", "KR", "KRW", 180_000, 0.0000, 0.020, "KOSPI", "STOCK"),
            new Symbol("069500", "KODEX 200", "KR", "KRW", 30_000, 0.0003, 0.010, "KOSPI", "ETF"),
            new Symbol("AAPL", "애플", "US", "USD", 125, 0.0006, 0.016, "NASDAQ", "STOCK"),
            new Symbol("NVDA", "엔비디아", "US", "USD", 15, 0.0022, 0.030, "NASDAQ", "STOCK"),
            new Symbol("TSLA", "테슬라", "US", "USD", 110, 0.0008, 0.035, "NASDAQ", "STOCK"),
            new Symbol("SPY", "SPDR S&P 500 ETF", "US", "USD", 380, 0.0005, 0.009, "AMEX", "ETF"));

    /** 종목 목록(GET /api/v1/stocks/all)에만 있는 종목. 기능 A의 실제 종목 검색·시뮬레이션을 로컬에서 보려고 둔다. */
    static final List<Symbol> LISTED_ONLY = List.of(
            new Symbol("102110", "TIGER 200", "KR", "KRW", 30_500, 0.0003, 0.010, "KOSPI", "ETF"),
            new Symbol("360750", "TIGER 미국S&P500", "KR", "KRW", 13_000, 0.0005, 0.010, "KOSPI", "ETF"),
            new Symbol("005935", "삼성전자우", "KR", "KRW", 47_000, 0.0003, 0.015, "KOSPI", "STOCK", false,
                    "KR7005931001", null),
            new Symbol("500001", "레버리지 ETN", "KR", "KRW", 10_000, 0.0, 0.020, "KOSPI", "ETN"),
            new Symbol("229200", "KODEX 코스닥150", "KR", "KRW", 12_000, 0.0001, 0.014, "KOSDAQ", "ETF"),
            new Symbol("247540", "에코프로비엠", "KR", "KRW", 120_000, 0.0002, 0.035, "KOSDAQ", "STOCK"),
            new Symbol("KO", "코카콜라", "US", "USD", 60, 0.0002, 0.009, "NYSE", "STOCK"),
            new Symbol("QQQ", "인베스코 QQQ", "US", "USD", 270, 0.0006, 0.012, "NASDAQ", "ETF"),
            new Symbol("VOO", "뱅가드 S&P 500 ETF", "US", "USD", 350, 0.0005, 0.009, "AMEX", "FOREIGN_ETF"),
            // 2025-06-02 상장: 상장 전 기간을 요청해도 그 앞 일봉은 없다
            new Symbol("NEWX", "뉴리스팅 테크", "US", "USD", 20, 0.0010, 0.030, "NASDAQ", "STOCK", true,
                    "US0NEWX00000", LocalDate.of(2025, 6, 2)));

    /** 시세가 있는 모든 종목(계좌 종목 + 목록 전용 종목). */
    static final List<Symbol> UNIVERSE = java.util.stream.Stream.concat(SYMBOLS.stream(), LISTED_ONLY.stream()).toList();

    static final Map<String, Symbol> BY_CODE = UNIVERSE.stream().collect(Collectors.toMap(Symbol::symbol, Function.identity()));

    private final LocalDate today;
    private final Map<String, NavigableMap<LocalDate, BigDecimal>> closes = new TreeMap<>();
    private final NavigableMap<LocalDate, BigDecimal> usdKrw = new TreeMap<>();

    FakeTossMarket(LocalDate today) {
        this.today = today;
        for (Symbol s : UNIVERSE) {
            Random rnd = new Random(s.symbol().hashCode() * 31L + 7);
            NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
            double price = s.start();
            LocalDate first = s.listDate() == null || s.listDate().isBefore(EPOCH) ? EPOCH : s.listDate();
            for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
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
