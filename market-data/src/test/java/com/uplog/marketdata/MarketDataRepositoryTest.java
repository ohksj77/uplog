package com.uplog.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uplog.calc.PriceSeries;
import com.uplog.core.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MarketDataRepositoryTest {

    private static final LocalDate MON = LocalDate.of(2026, 10, 12);

    private MarketDataRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MarketDataRepository(TestDatabase.migrated());
    }

    private long etf(String code, String name) {
        return repository.upsertInstrument(new NewInstrument(code, name, "KRX", CurrencyCode.KRW, InstrumentType.ETF, false));
    }

    private static DailyPrice price(LocalDate date, long close) {
        return DailyPrice.close(date, BigDecimal.valueOf(close));
    }

    @Test
    void 이름이나_코드_부분_일치로_시세가_있는_ETF만_이름순으로_찾는다() {
        long b = etf("TEST02", "나 테스트 ETF");
        long a = etf("TEST01", "가 테스트 ETF");
        etf("TEST03", "다 시세 없는 ETF");
        long stock = repository.upsertInstrument(
                new NewInstrument("STK001", "테스트 주식", "KRX", CurrencyCode.KRW, InstrumentType.STOCK, false));
        repository.insertDailyPrices(a, List.of(price(MON, 100), price(MON.plusDays(1), 101)));
        repository.insertDailyPrices(b, List.of(price(MON, 200)));
        repository.insertDailyPrices(stock, List.of(price(MON, 300)));

        assertThat(repository.searchEtfs("", 20))
                .extracting(l -> l.instrument().code())
                .containsExactly("TEST01", "TEST02");
        assertThat(repository.searchEtfs("test02", 20))
                .extracting(l -> l.instrument().code())
                .containsExactly("TEST02");
        assertThat(repository.searchEtfs("가 테스", 20))
                .singleElement()
                .satisfies(l -> {
                    assertThat(l.instrument().name()).isEqualTo("가 테스트 ETF");
                    assertThat(l.range()).contains(new PriceRange(MON, MON.plusDays(1)));
                });
        assertThat(repository.searchEtfs("ETF", 1)).hasSize(1);
    }

    @Test
    void 검색어의_와일드카드_문자는_글자_그대로_찾는다() {
        long id = etf("TEST01", "테스트 100% ETF");
        long other = etf("TEST02", "테스트 1000 ETF");
        repository.insertDailyPrices(id, List.of(price(MON, 100)));
        repository.insertDailyPrices(other, List.of(price(MON, 100)));

        assertThat(repository.searchEtfs("100%", 20))
                .extracting(l -> l.instrument().code())
                .containsExactly("TEST01");
        assertThat(repository.searchEtfs("_", 20)).isEmpty();
    }

    @Test
    void 구간_종가를_PriceSeries로_읽는다() {
        long id = etf("TEST01", "테스트 ETF");
        repository.insertDailyPrices(id, List.of(price(MON, 100), price(MON.plusDays(1), 110), price(MON.plusDays(2), 120)));

        PriceSeries series = repository.loadCloses(id, MON.plusDays(1), MON.plusDays(10)).orElseThrow();

        assertThat(series.firstDate()).isEqualTo(MON.plusDays(1));
        assertThat(series.lastDate()).isEqualTo(MON.plusDays(2));
        assertThat(series.closeOn(MON.plusDays(2))).isEqualByComparingTo("120");
        assertThat(repository.loadCloses(id, MON.minusDays(10), MON.minusDays(1))).isEmpty();
    }

    @Test
    void 종목별_첫_마지막_시세일을_구한다() {
        long id = etf("TEST01", "테스트 ETF");
        long empty = etf("TEST02", "시세 없는 ETF");
        repository.insertDailyPrices(id, List.of(price(MON, 100), price(MON.plusDays(3), 110)));

        assertThat(repository.priceRange(id)).contains(new PriceRange(MON, MON.plusDays(3)));
        assertThat(repository.priceRange(empty)).isEmpty();
        assertThat(repository.findByCode("TEST02")).hasValueSatisfying(l -> assertThat(l.range()).isEmpty());
        assertThat(repository.findByCode("NOPE")).isEmpty();
    }

    @Test
    void 같은_코드는_이름만_갱신하고_같은_날_시세는_덮어쓴다() {
        long id = etf("TEST01", "옛 이름");
        assertThat(etf("TEST01", "새 이름")).isEqualTo(id);
        repository.upsertDailyPrices(id, List.of(price(MON, 100)));
        repository.upsertDailyPrices(id, List.of(price(MON, 105), price(MON.plusDays(1), 106)));

        assertThat(repository.findByCode("TEST01").orElseThrow().instrument().name()).isEqualTo("새 이름");
        PriceSeries series = repository.loadCloses(id, MON, MON.plusDays(1)).orElseThrow();
        assertThat(series.closeOn(MON)).isEqualByComparingTo("105");
        assertThat(series.closeOn(MON.plusDays(1))).isEqualByComparingTo("106");
    }

    @Test
    void 종가는_양수여야_한다() {
        long id = etf("TEST01", "테스트 ETF");
        assertThatThrownBy(() -> repository.insertDailyPrices(id, List.of(price(MON, 0))))
                .isInstanceOf(RuntimeException.class);
    }
}
