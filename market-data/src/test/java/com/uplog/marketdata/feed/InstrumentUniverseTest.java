package com.uplog.marketdata.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.uplog.core.CurrencyCode;
import com.uplog.marketdata.DailyPrice;
import com.uplog.marketdata.Instrument;
import com.uplog.marketdata.InstrumentType;
import com.uplog.marketdata.ListedInstrument;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.NewInstrument;
import com.uplog.marketdata.TestDatabase;
import com.uplog.marketdata.feed.MarketFeed.Listing;
import com.uplog.marketdata.feed.MarketFeed.ListingMarket;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstrumentUniverseTest {

    private static final Instant NOW = Instant.parse("2026-10-16T01:00:00Z");

    private MarketDataRepository marketData;
    private FeedRepository feeds;
    private StubFeed feed;

    @BeforeEach
    void setUp() {
        var jdbc = TestDatabase.migrated();
        marketData = new MarketDataRepository(jdbc);
        feeds = new FeedRepository(jdbc);
        feed = new StubFeed();
        feed.listings.put(ListingMarket.KOSPI, List.of(
                new Listing("005930", "삼성전자", "STOCK"),
                new Listing("069500", "KODEX 200", "ETF"),
                new Listing("102110", "TIGER 200", "ETF"),
                new Listing("500001", "어떤 ETN", "ETN")));
        feed.listings.put(ListingMarket.KOSDAQ, List.of(new Listing("229200", "KODEX 코스닥150", "ETF")));
        feed.listings.put(ListingMarket.NASDAQ, List.of(
                new Listing("AAPL", "애플", "STOCK"), new Listing("QQQ", "인베스코 QQQ", "FOREIGN_ETF")));
        feed.listings.put(ListingMarket.NYSE, List.of(new Listing("TSM", "TSMC", "DEPOSITARY_RECEIPT")));
        feed.listings.put(ListingMarket.AMEX, List.of(new Listing("SPY", "SPDR S&P 500", "ETF")));
    }

    private InstrumentUniverse universe(Instant now) {
        return new InstrumentUniverse(feeds, marketData, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Instrument get(String code) {
        return marketData.findByCode(code).map(ListedInstrument::instrument).orElseThrow();
    }

    @Test
    void 다섯_시장의_ETF와_주식을_실제_종목으로_넣고_ETN은_뺀다() {
        assertThat(universe(NOW).ensureFresh(feed)).isEqualTo(5);

        assertThat(get("069500")).satisfies(i -> {
            assertThat(i.type()).isEqualTo(InstrumentType.ETF);
            assertThat(i.market()).isEqualTo("KR");
            assertThat(i.currency()).isEqualTo(CurrencyCode.KRW);
            assertThat(i.sample()).isFalse();
        });
        assertThat(get("QQQ").type()).isEqualTo(InstrumentType.ETF);
        assertThat(get("QQQ").currency()).isEqualTo(CurrencyCode.USD);
        assertThat(get("QQQ").market()).isEqualTo("US");
        assertThat(get("TSM").type()).isEqualTo(InstrumentType.STOCK);
        assertThat(marketData.findByCode("500001")).isEmpty();
    }

    @Test
    void 시장마다_하루에_한_번만_받는다() {
        universe(NOW).ensureFresh(feed);
        feed.calls.clear();

        assertThat(universe(NOW.plusSeconds(3_600)).ensureFresh(feed)).isZero();
        assertThat(feed.calls).isEmpty();

        // 다음 날(KST) 다시 받는다. 이름이 바뀌면 고친다.
        feed.listings.put(ListingMarket.KOSPI, List.of(new Listing("069500", "KODEX 200 (새 이름)", "ETF")));
        assertThat(universe(NOW.plusSeconds(86_400)).ensureFresh(feed)).isEqualTo(5);
        assertThat(get("069500").name()).isEqualTo("KODEX 200 (새 이름)");
    }

    @Test
    void 수집_작업이_주식으로_넣은_ETF는_ETF로_고치고_샘플_종목은_건드리지_않는다() {
        marketData.upsertInstrument(new NewInstrument("069500", "069500", "KR", CurrencyCode.KRW, InstrumentType.STOCK, false));
        marketData.upsertInstrument(new NewInstrument("SPY", "샘플 SPY", "SAMPLE", CurrencyCode.KRW, InstrumentType.ETF, true));

        universe(NOW).ensureFresh(feed);

        assertThat(get("069500").type()).isEqualTo(InstrumentType.ETF);
        assertThat(get("069500").name()).isEqualTo("KODEX 200");
        assertThat(get("SPY").name()).isEqualTo("샘플 SPY");
        assertThat(get("SPY").sample()).isTrue();
    }

    @Test
    void 목록에_없는_코드는_상세로_확인해_넣는다() {
        feed.details.put("0091C0", new MarketFeed.Detail("0091C0", "기타시장 ETF", CurrencyCode.KRW, "ETF", "ACTIVE"));
        feed.details.put("OLD", new MarketFeed.Detail("OLD", "상폐", CurrencyCode.USD, "STOCK", "DELISTED"));

        assertThat(universe(NOW).findOrFetch(feed, "0091C0")).map(l -> l.instrument().type()).contains(InstrumentType.ETF);
        assertThat(universe(NOW).findOrFetch(feed, "OLD")).isEmpty();
        assertThat(universe(NOW).findOrFetch(feed, "NOPE")).isEmpty();
        feed.calls.clear();
        assertThat(universe(NOW).findOrFetch(feed, "0091C0")).isPresent();
        assertThat(feed.calls).isEmpty();
    }

    @Test
    void 실제_종목_검색은_정확한_코드_ETF_주식_순이고_샘플_종목은_나오지_않는다() {
        universe(NOW).ensureFresh(feed);
        long sample = marketData.upsertInstrument(
                new NewInstrument("SAMPLE01", "샘플 200 ETF", "SAMPLE", CurrencyCode.KRW, InstrumentType.ETF, true));
        marketData.insertDailyPrices(sample, List.of(DailyPrice.close(LocalDate.of(2026, 10, 15), BigDecimal.TEN)));
        marketData.upsertInstrument(new NewInstrument("SAMPLE02", "샘플 시세 없는 200", "SAMPLE", CurrencyCode.KRW,
                InstrumentType.ETF, true));
        marketData.upsertInstrument(new NewInstrument("200", "이백전자", "KR", CurrencyCode.KRW, InstrumentType.STOCK, false));

        assertThat(feeds.searchRealInstruments("200", 10)).extracting(l -> l.instrument().code())
                .containsExactly("200", "069500", "229200", "102110"); // 코드 일치 주식 → ETF(이름순)
        assertThat(feeds.searchRealInstruments("", 3)).extracting(l -> l.instrument().type())
                .containsOnly(InstrumentType.ETF);
        assertThat(feeds.searchRealInstruments("s", 10)).extracting(l -> l.instrument().code())
                .startsWith("SPY").doesNotContain("SAMPLE01");
        assertThat(feeds.searchRealInstruments("200", 10).getFirst().range()).isEmpty();
        assertThat(feeds.searchRealInstruments("샘플", 10)).isEmpty();
    }
}
