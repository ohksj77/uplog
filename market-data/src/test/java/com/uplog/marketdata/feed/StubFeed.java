package com.uplog.marketdata.feed;

import com.uplog.core.CurrencyCode;
import com.uplog.marketdata.DailyPrice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 테스트용 시세 소스. 일봉은 토스 명세처럼 200개씩 최신순이고, nextBefore는 그 페이지 가장 오래된 봉의 시각(포함)이다.
 * 받은 요청을 기록한다.
 */
final class StubFeed implements MarketFeed {

    static final int PAGE = 200;

    final Map<ListingMarket, List<Listing>> listings = new EnumMap<>(ListingMarket.class);
    final Map<String, Detail> details = new HashMap<>();
    final Map<String, NavigableMap<LocalDate, BigDecimal>> closes = new HashMap<>();
    final Map<String, CurrencyCode> currencies = new HashMap<>();
    final NavigableMap<LocalDate, BigDecimal> fx = new TreeMap<>();
    final List<String> calls = new CopyOnWriteArrayList<>();
    volatile MarketFeedException failure;

    /** 평일마다 start에서 1씩 오르는 종가. */
    void weekdayCloses(String code, CurrencyCode currency, LocalDate from, LocalDate to, double start) {
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        double price = start;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() <= 5) {
                series.put(d, BigDecimal.valueOf(price));
                price += 1;
            }
        }
        closes.put(code, series);
        currencies.put(code, currency);
    }

    long count(String prefix) {
        return calls.stream().filter(c -> c.startsWith(prefix)).count();
    }

    private void check() {
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public List<Listing> listings(ListingMarket market) {
        calls.add("listings:" + market);
        check();
        return listings.getOrDefault(market, List.of());
    }

    @Override
    public List<Detail> details(List<String> codes) {
        calls.add("details:" + String.join(",", codes));
        check();
        return codes.stream().filter(details::containsKey).map(details::get).toList();
    }

    @Override
    public Optional<CandlePage> dailyCandles(String code, OffsetDateTime before) {
        calls.add("candles:" + code + ":" + (before == null ? "latest" : before.toString()));
        check();
        NavigableMap<LocalDate, BigDecimal> series = closes.get(code);
        if (series == null) {
            return Optional.empty();
        }
        ZoneId zone = CandleBackfill.zone(currencies.get(code));
        List<DailyPrice> page = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> e : series.descendingMap().entrySet()) {
            OffsetDateTime at = e.getKey().atStartOfDay(zone).toOffsetDateTime();
            if (before != null && at.isAfter(before)) {
                continue;
            }
            page.add(DailyPrice.close(e.getKey(), e.getValue()));
            if (page.size() == PAGE) {
                break;
            }
        }
        boolean more = !page.isEmpty() && series.lowerKey(page.getLast().date()) != null;
        OffsetDateTime next = more ? page.getLast().date().atStartOfDay(zone).toOffsetDateTime() : null;
        return Optional.of(new CandlePage(page, next));
    }

    @Override
    public Optional<BigDecimal> usdKrw(LocalDate date) {
        calls.add("fx:" + date);
        check();
        return Optional.ofNullable(fx.get(date));
    }
}
