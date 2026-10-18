package com.uplog.marketdata.publicdata;

import static org.assertj.core.api.Assertions.assertThat;

import com.uplog.marketdata.EtfQuote;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.PriceDataVersion;
import com.uplog.marketdata.PriceRange;
import com.uplog.marketdata.TestDatabase;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EtfPriceIngestJobTest {

    private final MarketDataRepository repository = new MarketDataRepository(TestDatabase.migrated());
    private final PriceDataVersion version = new PriceDataVersion();

    @Test
    void 최근_평일마다_받아_종목과_시세를_저장한다() {
        LocalDate sat = LocalDate.of(2026, 10, 17);
        List<LocalDate> requested = new ArrayList<>();
        Clock clock = Clock.fixed(sat.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        EtfPriceIngestJob job = new EtfPriceIngestJob(date -> {
            requested.add(date);
            return List.of(new EtfQuote(date, "TEST01", "테스트 ETF", BigDecimal.valueOf(1000 + date.getDayOfMonth())));
        }, repository, version, clock, 7);

        job.runScheduled();

        // 10/10(토)–10/16(금) 중 평일만
        assertThat(requested).containsExactly(
                LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 14),
                LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16));
        var listed = repository.findByCode("TEST01").orElseThrow();
        assertThat(listed.instrument().sample()).isFalse();
        assertThat(listed.range()).contains(new PriceRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 16)));
        assertThat(version.current()).isPositive();
    }

    @Test
    void 하루_실패해도_나머지_날은_수집한다() {
        LocalDate mon = LocalDate.of(2026, 10, 19);
        Clock clock = Clock.fixed(mon.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
        EtfPriceIngestJob job = new EtfPriceIngestJob(date -> {
            if (date.getDayOfMonth() == 15) {
                throw new PublicDataApiException("일시 오류");
            }
            return List.of(new EtfQuote(date, "TEST01", "테스트 ETF", BigDecimal.TEN));
        }, repository, version, clock, 7);

        job.runScheduled();

        long id = repository.findByCode("TEST01").orElseThrow().instrument().id();
        assertThat(repository.loadCloses(id, LocalDate.of(2026, 10, 12), mon).orElseThrow()
                        .tradingDays(LocalDate.of(2026, 10, 12), mon))
                .containsExactly(
                        LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13),
                        LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 16));
    }
}
