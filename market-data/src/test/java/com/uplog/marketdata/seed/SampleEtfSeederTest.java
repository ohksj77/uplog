package com.uplog.marketdata.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.uplog.marketdata.DailyPrice;
import com.uplog.marketdata.ListedInstrument;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.PriceDataVersion;
import com.uplog.marketdata.TestDatabase;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class SampleEtfSeederTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    void 가장_최근_평일에서_끝나는_약_4년치_평일_시세를_만든다() {
        LocalDate sun = LocalDate.of(2026, 10, 18);
        MarketDataRepository repository = new MarketDataRepository(TestDatabase.migrated());
        SampleEtfSeeder seeder = new SampleEtfSeeder(
                repository, new PriceDataVersion(), Clock.fixed(sun.atStartOfDay(SEOUL).toInstant(), SEOUL));

        seeder.run(null);
        seeder.run(null); // 두 번 돌려도 중복 삽입하지 않는다

        List<ListedInstrument> etfs = repository.searchEtfs("샘플", 20);
        assertThat(etfs).hasSize(4).allSatisfy(l -> {
            assertThat(l.instrument().code()).startsWith("SAMPLE");
            assertThat(l.instrument().name()).startsWith("샘플 ");
            assertThat(l.instrument().sample()).isTrue();
            assertThat(l.range()).hasValueSatisfying(r -> {
                assertThat(r.lastDate()).isEqualTo(LocalDate.of(2026, 10, 16));
                assertThat(r.firstDate()).isBetween(LocalDate.of(2022, 10, 17), LocalDate.of(2022, 10, 19));
            });
        });
    }

    @Test
    void 같은_종료일이면_항상_같은_시세이고_주말은_없다() {
        LocalDate end = LocalDate.of(2026, 10, 16);
        List<DailyPrice> first = SampleEtfSeeder.prices(SampleEtfSeeder.SAMPLES.getFirst(), end);
        List<DailyPrice> second = SampleEtfSeeder.prices(SampleEtfSeeder.SAMPLES.getFirst(), end);

        assertThat(first).isEqualTo(second).hasSizeGreaterThan(1000);
        assertThat(first).allSatisfy(p -> {
            assertThat(p.date().getDayOfWeek()).isNotIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
            assertThat(p.close().signum()).isPositive();
        });
    }

    @Test
    void 주말이면_직전_금요일을_종료일로_본다() {
        assertThat(SampleEtfSeeder.lastWeekday(LocalDate.of(2026, 10, 18))).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(SampleEtfSeeder.lastWeekday(LocalDate.of(2026, 10, 16))).isEqualTo(LocalDate.of(2026, 10, 16));
    }
}
