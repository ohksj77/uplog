package com.uplog.ingest;

import com.uplog.core.CurrencyCode;
import com.uplog.core.TradingCalendar;
import com.uplog.core.WeekdayTradingCalendar;
import com.uplog.marketdata.DailyPrice;
import com.uplog.marketdata.InstrumentType;
import com.uplog.marketdata.ListedInstrument;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.NewInstrument;
import com.uplog.marketdata.PriceDataVersion;
import com.uplog.marketdata.PriceRange;
import com.uplog.portfolio.broker.BrokerPort;
import com.uplog.portfolio.connect.BrokerCredentials;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

/**
 * 계좌가 거래한 종목의 일봉과 USD/KRW를 공용 시세 테이블(market-data)에 채운다. 이미 있는 날짜는 다시 받지 않는다.
 * 증권사가 종목을 모르면(상장폐지 404) 그 종목의 체결가를 종가로 쓰고 이후 평일은 마지막 값을 잇는다.
 */
public class MarketDataSync {

    public static final String USD_KRW = "USDKRW";
    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 일별 환율은 서울 외환시장 정규 마감 시각 기준으로 받는다. */
    static final LocalTime FX_TIME = LocalTime.of(15, 30);

    private static final Logger log = LoggerFactory.getLogger(MarketDataSync.class);

    private final BrokerPort broker;
    private final MarketDataRepository marketData;
    private final PriceDataVersion priceVersion;
    private final TradingCalendar weekdays = WeekdayTradingCalendar.weekdaysOnly();

    public MarketDataSync(BrokerPort broker, MarketDataRepository marketData, PriceDataVersion priceVersion) {
        this.broker = broker;
        this.marketData = marketData;
        this.priceVersion = priceVersion;
    }

    /** 종목이 없으면 만든다. 이름을 모르면(null) 코드로 두고, 이미 있으면 이름이 코드일 때만 바꾼다. */
    public void ensureInstrument(String symbol, String name, BrokerPort.Market market, CurrencyCode currency) {
        Optional<ListedInstrument> existing = marketData.findByCode(symbol);
        if (existing.isPresent()) {
            String current = existing.get().instrument().name();
            if (name != null && !name.equals(current) && current.equals(symbol)) {
                marketData.upsertInstrument(new NewInstrument(symbol, name, existing.get().instrument().market(),
                        existing.get().instrument().currency(), existing.get().instrument().type(), false));
            }
            return;
        }
        marketData.upsertInstrument(new NewInstrument(symbol, name == null ? symbol : name, market.name(), currency,
                InstrumentType.STOCK, false));
    }

    /** firstTrade 기준 lookback 거래일(평일) 전. */
    public LocalDate lookbackStart(LocalDate firstTrade, int lookback) {
        LocalDate d = firstTrade;
        for (int i = 0; i < lookback; i++) {
            d = weekdays.previous(d);
        }
        return d;
    }

    /**
     * [from, to] 일봉을 채운다. 이미 from까지 시세가 있으면 마지막 시세 며칠 전부터만 다시 받는다(정정 반영).
     *
     * @param fallback 증권사가 종목을 모를 때 쓸 날짜별 체결가
     * @return 저장한 일봉 수
     */
    public int syncCandles(BrokerCredentials credentials, String symbol, LocalDate from, LocalDate to,
            NavigableMap<LocalDate, BigDecimal> fallback) {
        long instrumentId = marketData.findByCode(symbol).orElseThrow().instrument().id();
        Optional<PriceRange> range = marketData.priceRange(instrumentId);
        LocalDate need = from;
        if (range.isPresent() && !range.get().firstDate().isAfter(from)) {
            need = range.get().lastDate().minusDays(7);
            if (need.isBefore(from)) {
                need = from;
            }
        }
        if (need.isAfter(to)) {
            return 0;
        }
        List<DailyPrice> prices = new ArrayList<>();
        OffsetDateTime before = null;
        boolean delisted = false;
        while (true) {
            Optional<BrokerPort.CandlePage> page = broker.dailyCandles(credentials, symbol, before);
            if (page.isEmpty()) {
                delisted = true;
                break;
            }
            LocalDate oldest = null;
            for (BrokerPort.Candle c : page.get().candles()) {
                if (!c.date().isBefore(need) && !c.date().isAfter(to)) {
                    prices.add(DailyPrice.close(c.date(), c.close()));
                }
                oldest = oldest == null || c.date().isBefore(oldest) ? c.date() : oldest;
            }
            if (oldest == null || !oldest.isAfter(need) || page.get().nextBefore() == null) {
                break;
            }
            before = page.get().nextBefore();
        }
        if (delisted) {
            prices = carried(fallback, need, to);
            log.warn("일봉이 없는 종목(상장폐지?) {}: 체결가로 {}일 채움", symbol, prices.size());
        }
        if (!prices.isEmpty()) {
            marketData.upsertDailyPrices(instrumentId, prices);
            priceVersion.bump();
        }
        return prices.size();
    }

    /** [from, to] 평일 중 환율이 없는 날만 받는다. @return 새로 넣은 날 수 */
    public int syncFx(BrokerCredentials credentials, LocalDate from, LocalDate to, ProgressListener progress) {
        Set<LocalDate> have = marketData.loadFxRates(USD_KRW, from, to)
                .map(s -> Set.copyOf(s.tradingDays(from, to)))
                .orElse(Set.of());
        List<LocalDate> missing = weekdays.tradingDays(from, to).stream().filter(d -> !have.contains(d)).toList();
        int added = 0;
        for (int i = 0; i < missing.size(); i++) {
            LocalDate d = missing.get(i);
            Optional<BigDecimal> rate = broker.usdKrw(credentials, d.atTime(FX_TIME).atZone(SEOUL).toOffsetDateTime());
            if (rate.isPresent()) {
                try {
                    marketData.insertFxRates(USD_KRW, List.of(DailyPrice.close(d, rate.get())));
                    added++;
                } catch (DuplicateKeyException e) {
                    // 다른 계좌의 작업이 먼저 넣었다
                }
            }
            progress.report((i + 1.0) / missing.size());
        }
        if (added > 0) {
            priceVersion.bump();
        }
        return added;
    }

    public Optional<LocalDate> lastFxDate() {
        return marketData.fxRange(USD_KRW).map(PriceRange::lastDate);
    }

    private List<DailyPrice> carried(NavigableMap<LocalDate, BigDecimal> fallback, LocalDate from, LocalDate to) {
        List<DailyPrice> prices = new ArrayList<>();
        if (fallback == null || fallback.isEmpty()) {
            return prices;
        }
        Map<LocalDate, BigDecimal> byDay = new TreeMap<>();
        for (LocalDate d : weekdays.tradingDays(from, to)) {
            Map.Entry<LocalDate, BigDecimal> e = fallback.floorEntry(d);
            BigDecimal price = e != null ? e.getValue() : fallback.firstEntry().getValue();
            byDay.put(d, price);
        }
        byDay.forEach((d, p) -> prices.add(DailyPrice.close(d, p)));
        return prices;
    }

    /** 0.0–1.0 진행률. */
    @FunctionalInterface
    public interface ProgressListener {
        void report(double fraction);

        ProgressListener NONE = f -> {};
    }
}
