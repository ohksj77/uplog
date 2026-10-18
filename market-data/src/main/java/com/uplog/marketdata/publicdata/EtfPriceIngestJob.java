package com.uplog.marketdata.publicdata;

import com.uplog.core.CurrencyCode;
import com.uplog.core.TradingCalendar;
import com.uplog.core.WeekdayTradingCalendar;
import com.uplog.marketdata.DailyPrice;
import com.uplog.marketdata.EtfQuote;
import com.uplog.marketdata.InstrumentType;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.NewInstrument;
import com.uplog.marketdata.PriceDataVersion;
import com.uplog.marketdata.PriceSource;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 국내 ETF 일별 종가 수집. 공공데이터는 보통 다음 영업일 오후에 공개되므로 매 평일 오후에
 * 최근 며칠을 다시 받아 덮어쓴다(멱등).
 *
 * <p>휴장일 캘린더가 아직 없어 평일만 요청한다. 휴장일에는 빈 응답이 오고 행이 생기지 않으므로
 * {@code daily_price}의 날짜가 곧 실제 거래일이 된다.
 */
public class EtfPriceIngestJob {

    private static final Logger log = LoggerFactory.getLogger(EtfPriceIngestJob.class);

    private final PriceSource source;
    private final MarketDataRepository repository;
    private final PriceDataVersion version;
    private final Clock clock;
    private final int lookbackDays;
    private final TradingCalendar calendar = WeekdayTradingCalendar.weekdaysOnly();

    public EtfPriceIngestJob(
            PriceSource source, MarketDataRepository repository, PriceDataVersion version, Clock clock, int lookbackDays) {
        this.source = source;
        this.repository = repository;
        this.version = version;
        this.clock = clock;
        this.lookbackDays = lookbackDays;
    }

    @Scheduled(cron = "${uplog.market-data.public-data.cron:0 30 14 * * MON-FRI}", zone = "Asia/Seoul")
    public void runScheduled() {
        LocalDate today = LocalDate.now(clock);
        for (LocalDate date : calendar.tradingDays(today.minusDays(lookbackDays), today.minusDays(1))) {
            try {
                ingest(date);
            } catch (RuntimeException e) {
                log.warn("ETF 시세 수집 실패 {}: {}", date, e.getMessage(), e);
            }
        }
    }

    /** @return 저장한 시세 건수 */
    public int ingest(LocalDate date) {
        List<EtfQuote> quotes = source.etfQuotes(date);
        for (EtfQuote q : quotes) {
            long id = repository.upsertInstrument(
                    new NewInstrument(q.code(), q.name(), "KRX", CurrencyCode.KRW, InstrumentType.ETF, false));
            repository.upsertDailyPrices(id, List.of(DailyPrice.close(q.date(), q.close())));
        }
        if (!quotes.isEmpty()) {
            version.bump();
        }
        log.info("ETF 시세 {}: {}건", date, quotes.size());
        return quotes.size();
    }
}
