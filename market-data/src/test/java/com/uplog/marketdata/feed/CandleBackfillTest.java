package com.uplog.marketdata.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.uplog.core.CurrencyCode;
import com.uplog.marketdata.DailyPrice;
import com.uplog.marketdata.Instrument;
import com.uplog.marketdata.InstrumentType;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.NewInstrument;
import com.uplog.marketdata.PriceDataVersion;
import com.uplog.marketdata.PriceRange;
import com.uplog.marketdata.TestDatabase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class CandleBackfillTest {

    /** 2026-10-16(금) 10:00 KST = 2026-10-15 21:00 NY */
    private static final Instant NOW = Instant.parse("2026-10-16T01:00:00Z");
    private static final LocalDate KR_YESTERDAY = LocalDate.of(2026, 10, 15);

    private JdbcTemplate jdbc;
    private MarketDataRepository marketData;
    private FeedRepository feeds;
    private PriceDataVersion version;
    private StubFeed feed;

    @BeforeEach
    void setUp() {
        jdbc = TestDatabase.migrated();
        marketData = new MarketDataRepository(jdbc);
        feeds = new FeedRepository(jdbc);
        version = new PriceDataVersion();
        feed = new StubFeed();
        feed.weekdayCloses("069500", CurrencyCode.KRW, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 10, 16), 10_000);
        feed.weekdayCloses("SPY", CurrencyCode.USD, LocalDate.of(2020, 1, 1), LocalDate.of(2026, 10, 15), 300);
        feed.weekdayCloses("NEWX", CurrencyCode.USD, LocalDate.of(2025, 6, 2), LocalDate.of(2026, 10, 15), 20);
    }

    private CandleBackfill backfill(Instant now) {
        return new CandleBackfill(feeds, marketData, version, Clock.fixed(now, ZoneOffset.UTC));
    }

    private Instrument instrument(String code, CurrencyCode currency) {
        marketData.upsertInstrument(new NewInstrument(code, code, currency == CurrencyCode.USD ? "US" : "KR", currency,
                InstrumentType.ETF, false));
        return marketData.findByCode(code).orElseThrow().instrument();
    }

    private long days(Instrument i) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM daily_price WHERE instrument_id = ?", Long.class, i.id());
    }

    @Test
    void 처음에는_요청_구간을_받고_오늘_봉은_저장하지_않는다() {
        Instrument kodex = instrument("069500", CurrencyCode.KRW);

        assertThat(backfill(NOW).ensure(feed, kodex, LocalDate.of(2025, 10, 1))).isTrue();

        PriceRange range = marketData.priceRange(kodex.id()).orElseThrow();
        assertThat(range.firstDate()).isEqualTo(LocalDate.of(2025, 10, 1));
        assertThat(range.lastDate()).isEqualTo(KR_YESTERDAY); // 10-16(오늘, 장중) 봉은 뺀다
        assertThat(feeds.coverage(kodex.id())).contains(new FeedRepository.Coverage(LocalDate.of(2025, 10, 1), KR_YESTERDAY));
        assertThat(feed.count("candles:069500")).isEqualTo(2); // 약 262거래일 = 200 + 나머지
        assertThat(version.current()).isPositive();
    }

    @Test
    void 이미_확인한_구간은_다시_받지_않고_모자란_앞쪽과_뒤쪽만_받는다() {
        Instrument kodex = instrument("069500", CurrencyCode.KRW);
        CandleBackfill backfill = backfill(NOW);
        backfill.ensure(feed, kodex, LocalDate.of(2025, 10, 1));
        feed.calls.clear();

        // 같은 날 같은 구간: 호출 없음
        backfill.ensure(feed, kodex, LocalDate.of(2025, 10, 1));
        backfill.ensure(feed, kodex, LocalDate.of(2026, 1, 5));
        assertThat(feed.calls).isEmpty();

        // 앞쪽만 모자라면 확인 구간 시작 직전부터 과거로
        backfill.ensure(feed, kodex, LocalDate.of(2024, 10, 1));
        assertThat(feed.calls).hasSize(2)
                .allSatisfy(c -> assertThat(c).doesNotContain("latest"));
        assertThat(feed.calls.getFirst()).startsWith("candles:069500:2025-09-30T23:59:59+09:00");
        assertThat(marketData.priceRange(kodex.id()).orElseThrow().firstDate()).isEqualTo(LocalDate.of(2024, 10, 1));
        long stored = days(kodex);
        assertThat(stored).isEqualTo(feed.closes.get("069500").subMap(LocalDate.of(2024, 10, 1), true, KR_YESTERDAY, true).size());

        // 다음 날: 뒤쪽만 최신부터 한 페이지
        feed.calls.clear();
        CandleBackfill tomorrow = backfill(NOW.plusSeconds(86_400));
        tomorrow.ensure(feed, kodex, LocalDate.of(2024, 10, 1));
        assertThat(feed.calls).containsExactly("candles:069500:latest");
        assertThat(marketData.priceRange(kodex.id()).orElseThrow().lastDate()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(days(kodex)).isEqualTo(stored + 1);
    }

    @Test
    void 정정된_종가는_고치고_수집_작업이_넣은_시세는_확인_구간으로_본다() {
        Instrument kodex = instrument("069500", CurrencyCode.KRW);
        // 수집 작업이 넣어 둔 구간(확인 기록 없음)
        marketData.insertDailyPrices(kodex.id(), List.of(
                DailyPrice.close(LocalDate.of(2026, 10, 12), new BigDecimal("1")),
                DailyPrice.close(LocalDate.of(2026, 10, 13), feed.closes.get("069500").get(LocalDate.of(2026, 10, 13)))));

        backfill(NOW).ensure(feed, kodex, LocalDate.of(2026, 10, 12));

        assertThat(feed.calls).containsExactly("candles:069500:latest");
        assertThat(marketData.loadCloses(kodex.id(), LocalDate.of(2026, 10, 12), KR_YESTERDAY).orElseThrow().closeOn(
                LocalDate.of(2026, 10, 12))).isEqualByComparingTo(feed.closes.get("069500").get(LocalDate.of(2026, 10, 12)));
        assertThat(days(kodex)).isEqualTo(4); // 12, 13, 14, 15
    }

    @Test
    void 상장_전_구간을_요청해도_한_번_확인하면_다시_받지_않는다() {
        Instrument newx = instrument("NEWX", CurrencyCode.USD);
        CandleBackfill backfill = backfill(NOW);

        backfill.ensure(feed, newx, LocalDate.of(2023, 10, 1));
        long first = feed.count("candles:NEWX");
        backfill.ensure(feed, newx, LocalDate.of(2023, 10, 1));

        assertThat(feed.count("candles:NEWX")).isEqualTo(first);
        assertThat(marketData.priceRange(newx.id()).orElseThrow().firstDate()).isEqualTo(LocalDate.of(2025, 6, 2));
        // 미국은 뉴욕 기준 어제(10-14)까지. 10-15 봉은 뉴욕에서 아직 오늘이다.
        assertThat(marketData.priceRange(newx.id()).orElseThrow().lastDate()).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    void 증권사가_모르는_종목은_false다() {
        Instrument gone = instrument("GONE", CurrencyCode.KRW);
        assertThat(backfill(NOW).ensure(feed, gone, LocalDate.of(2026, 1, 1))).isFalse();
        assertThat(feeds.coverage(gone.id())).isEmpty();
    }
}
