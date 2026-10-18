package com.uplog.marketdata.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.uplog.calc.PriceSeries;
import com.uplog.marketdata.DailyPrice;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.PriceDataVersion;
import com.uplog.marketdata.TestDatabase;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FxBackfillTest {

    private MarketDataRepository marketData;
    private FxBackfill fx;
    private StubFeed feed;

    @BeforeEach
    void setUp() {
        marketData = new MarketDataRepository(TestDatabase.migrated());
        fx = new FxBackfill(marketData, new PriceDataVersion());
        feed = new StubFeed();
        for (LocalDate d = LocalDate.of(2023, 1, 2); d.isBefore(LocalDate.of(2026, 10, 17)); d = d.plusDays(1)) {
            feed.fx.put(d, BigDecimal.valueOf(1300 + d.getDayOfMonth()));
        }
    }

    @Test
    void 가까운_환율이_없는_날만_받고_이미_있으면_받지_않는다() {
        marketData.insertFxRates(FxBackfill.PAIR, List.of(DailyPrice.close(LocalDate.of(2026, 3, 2), new BigDecimal("1400"))));
        List<LocalDate> needed = List.of(LocalDate.of(2026, 3, 6), LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 4));

        assertThat(fx.ensure(feed, needed)).isEqualTo(2); // 3-06은 3-02 환율이 7일 안에 있다
        assertThat(feed.calls).containsExactly("fx:2026-04-01", "fx:2026-05-04");

        feed.calls.clear();
        assertThat(fx.ensure(feed, needed)).isZero();
        assertThat(feed.calls).isEmpty();
    }

    @Test
    void 받을_날이_많으면_달마다_첫날과_마지막날로_묶는다() {
        List<LocalDate> daily = new ArrayList<>();
        for (LocalDate d = LocalDate.of(2023, 10, 16); !d.isAfter(LocalDate.of(2026, 10, 16)); d = d.plusDays(1)) {
            if (d.getDayOfWeek().getValue() <= 5) {
                daily.add(d);
            }
        }
        List<LocalDate> picked = FxBackfill.bounded(daily);
        assertThat(picked).hasSizeLessThanOrEqualTo(FxBackfill.MAX_FETCH)
                .contains(LocalDate.of(2023, 10, 16), LocalDate.of(2026, 10, 16))
                .isSorted();

        fx.ensure(feed, daily);
        assertThat(feed.count("fx:")).isLessThanOrEqualTo(FxBackfill.MAX_FETCH);
    }

    @Test
    void 원화_환산은_그날_없으면_직전_구간_앞은_첫_환율을_쓴다() {
        Map<LocalDate, BigDecimal> closes = new TreeMap<>(Map.of(
                LocalDate.of(2026, 3, 2), new BigDecimal("10"),
                LocalDate.of(2026, 3, 3), new BigDecimal("11"),
                LocalDate.of(2026, 3, 5), new BigDecimal("12")));
        PriceSeries rates = new PriceSeries(Map.of(
                LocalDate.of(2026, 3, 3), new BigDecimal("1300"),
                LocalDate.of(2026, 3, 4), new BigDecimal("1400")));

        PriceSeries krw = FxBackfill.toKrw(new PriceSeries(closes), Optional.of(rates)).orElseThrow();

        assertThat(krw.closeOn(LocalDate.of(2026, 3, 2))).isEqualByComparingTo("13000");
        assertThat(krw.closeOn(LocalDate.of(2026, 3, 3))).isEqualByComparingTo("14300");
        assertThat(krw.closeOn(LocalDate.of(2026, 3, 5))).isEqualByComparingTo("16800");
        assertThat(FxBackfill.toKrw(new PriceSeries(closes), Optional.empty())).isEmpty();
    }
}
