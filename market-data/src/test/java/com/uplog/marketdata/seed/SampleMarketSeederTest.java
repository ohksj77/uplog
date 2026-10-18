package com.uplog.marketdata.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.uplog.marketdata.DailyPrice;
import com.uplog.marketdata.ListedInstrument;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.PriceDataVersion;
import com.uplog.marketdata.TestDatabase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SampleMarketSeederTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate FRI = LocalDate.of(2026, 10, 16);

    @Test
    void 샘플_종목_8개_지수_2개_환율을_가장_최근_평일까지_시드한다() {
        MarketDataRepository repository = new MarketDataRepository(TestDatabase.migrated());
        SampleMarketSeeder seeder = new SampleMarketSeeder(
                repository, new PriceDataVersion(), Clock.fixed(FRI.plusDays(2).atStartOfDay(SEOUL).toInstant(), SEOUL));

        seeder.run(null);
        seeder.run(null); // 두 번 돌려도 중복 삽입하지 않는다

        for (String code : List.of("S00001", "S00002", "S00003", "S00004", "SMPT", "SMPC", "SMPS", "SMPE", "SPX", "KOSPI200")) {
            ListedInstrument listed = repository.findByCode(code).orElseThrow();
            assertThat(listed.instrument().sample()).isTrue();
            assertThat(listed.range()).hasValueSatisfying(r -> assertThat(r.lastDate()).isEqualTo(FRI));
        }
        assertThat(repository.findByCode("SMPT").orElseThrow().instrument().market()).isEqualTo("US");
        // 합성 환율은 데모 전용 pair에만 넣고, 실제 계좌가 쓰는 USDKRW는 비워 둔다(역산이 실제 환율로 채운다)
        assertThat(repository.loadFxRates(SampleMarketSeeder.SAMPLE_FX_PAIR, FRI.minusYears(1), FRI)).hasValueSatisfying(fx -> {
            assertThat(fx.lastDate()).isEqualTo(FRI);
            assertThat(fx.closeOn(FRI)).isBetween(new BigDecimal("1100"), new BigDecimal("1600"));
        });
        assertThat(repository.fxRange(SampleMarketSeeder.USD_KRW)).isEmpty();
        // 원화 ETF만 기능 A 검색에 나온다: S00003은 나오고 SMPS(달러)는 안 나온다
        assertThat(repository.searchEtfs("", 20)).extracting(l -> l.instrument().code()).containsExactly("S00003");
    }

    @Test
    void 같은_종료일이면_같은_값이고_ETF는_지수를_따라간다() {
        Map<String, List<DailyPrice>> first = SampleMarketSeeder.generate(FRI);
        assertThat(first).isEqualTo(SampleMarketSeeder.generate(FRI));
        assertThat(first.get("S00001")).hasSizeGreaterThan(700);
        assertThat(first.get("S00001").getFirst().close().scale()).isZero();
        assertThat(first.get("SMPT").getFirst().close().scale()).isEqualTo(2);

        double spx = first.get("SPX").getLast().close().doubleValue();
        double smps = first.get("SMPS").getLast().close().doubleValue();
        assertThat(smps / spx).isBetween(0.0118, 0.0122);
    }
}
