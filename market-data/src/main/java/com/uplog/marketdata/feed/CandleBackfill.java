package com.uplog.marketdata.feed;

import com.uplog.core.CurrencyCode;
import com.uplog.marketdata.DailyPrice;
import com.uplog.marketdata.Instrument;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.PriceDataVersion;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 요청한 구간의 수정주가 일봉을 필요할 때 받아 daily_price에 채운다. 이미 확인한 구간({@link FeedRepository.Coverage})
 * 밖만 받는다: 앞쪽이 모자라면 확인 구간 시작 전부터, 뒤쪽이 모자라면 최신부터(마지막 며칠은 정정 반영으로 다시) 받는다.
 *
 * <p>그 시장의 오늘 봉은 장중에 값이 바뀌므로 저장하지 않는다. 확인 구간의 끝은 "현지 어제"이고, 하루에 한 번만 뒤쪽을 다시 받는다.
 */
@Component
public class CandleBackfill {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    /** 뒤쪽을 다시 받을 때 정정 반영으로 겹쳐 받는 날 수 */
    static final int CORRECTION_DAYS = 7;
    /** 한 번에 따라가는 최대 페이지(200봉씩, 약 24년) */
    static final int MAX_PAGES = 30;

    private static final Logger log = LoggerFactory.getLogger(CandleBackfill.class);

    private final FeedRepository repository;
    private final MarketDataRepository marketData;
    private final PriceDataVersion version;
    private final Clock clock;
    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Autowired
    public CandleBackfill(FeedRepository repository, MarketDataRepository marketData, PriceDataVersion version) {
        this(repository, marketData, version, Clock.systemUTC());
    }

    CandleBackfill(FeedRepository repository, MarketDataRepository marketData, PriceDataVersion version, Clock clock) {
        this.repository = repository;
        this.marketData = marketData;
        this.version = version;
        this.clock = clock;
    }

    /** 그 시장의 마지막으로 끝난 날(현지 어제). */
    public LocalDate lastCompleteDay(CurrencyCode currency) {
        return LocalDate.now(clock.withZone(zone(currency))).minusDays(1);
    }

    /**
     * [from, 현지 어제] 일봉이 확인되도록 모자란 부분만 받는다.
     *
     * @return 증권사가 종목을 몰라(상장폐지 등) 하나도 받지 못했으면 false
     */
    public boolean ensure(MarketFeed feed, Instrument instrument, LocalDate from) {
        ReentrantLock lock = locks.computeIfAbsent(instrument.id(), id -> new ReentrantLock());
        lock.lock();
        try {
            return ensureLocked(feed, instrument, from);
        } finally {
            lock.unlock();
        }
    }

    private boolean ensureLocked(MarketFeed feed, Instrument instrument, LocalDate from) {
        ZoneId zone = zone(instrument.currency());
        LocalDate to = lastCompleteDay(instrument.currency());
        Optional<FeedRepository.Coverage> current = repository.coverage(instrument.id());
        if (current.isEmpty()) {
            Optional<List<DailyPrice>> got = fetch(feed, instrument.code(), null, from, from, to);
            if (got.isEmpty()) {
                return false;
            }
            store(instrument.id(), got.get());
            repository.saveCoverage(instrument.id(), new FeedRepository.Coverage(from, to), clock.instant());
            return true;
        }
        FeedRepository.Coverage coverage = current.get();
        LocalDate newFrom = coverage.from();
        LocalDate newTo = coverage.to();
        if (coverage.to().isBefore(to)) {
            LocalDate keepFrom = coverage.to().minusDays(CORRECTION_DAYS);
            if (keepFrom.isBefore(coverage.from())) {
                keepFrom = coverage.from();
            }
            fetch(feed, instrument.code(), null, keepFrom, keepFrom, to).ifPresent(p -> store(instrument.id(), p));
            newTo = to;
        }
        if (coverage.from().isAfter(from)) {
            OffsetDateTime before = coverage.from().atStartOfDay(zone).minusSeconds(1).toOffsetDateTime();
            fetch(feed, instrument.code(), before, from, from, coverage.from().minusDays(1))
                    .ifPresent(p -> store(instrument.id(), p));
            newFrom = from;
        }
        if (!newFrom.equals(coverage.from()) || !newTo.equals(coverage.to())) {
            repository.saveCoverage(instrument.id(), new FeedRepository.Coverage(newFrom, newTo), clock.instant());
        }
        return true;
    }

    /**
     * before부터 과거로 페이지를 따라가며 [keepFrom, keepTo] 봉을 모은다. 가장 오래된 봉이 stopAt 이하가 되거나 더 없으면 멈춘다.
     *
     * @return 첫 페이지부터 종목이 없으면 empty
     */
    private Optional<List<DailyPrice>> fetch(
            MarketFeed feed, String code, OffsetDateTime before, LocalDate stopAt, LocalDate keepFrom, LocalDate keepTo) {
        Map<LocalDate, DailyPrice> kept = new TreeMap<>();
        OffsetDateTime cursor = before;
        for (int page = 0; page < MAX_PAGES; page++) {
            Optional<MarketFeed.CandlePage> got = feed.dailyCandles(code, cursor);
            if (got.isEmpty()) {
                if (page == 0) {
                    return Optional.empty();
                }
                break;
            }
            LocalDate oldest = null;
            for (DailyPrice c : got.get().candles()) {
                if (!c.date().isBefore(keepFrom) && !c.date().isAfter(keepTo)) {
                    kept.put(c.date(), c);
                }
                oldest = oldest == null || c.date().isBefore(oldest) ? c.date() : oldest;
            }
            OffsetDateTime next = got.get().nextBefore();
            if (oldest == null || !oldest.isAfter(stopAt) || next == null || Objects.equals(next, cursor)) {
                break;
            }
            cursor = next;
        }
        return Optional.of(new ArrayList<>(kept.values()));
    }

    /** 없는 날은 한꺼번에 넣고, 값이 바뀐 날만 고친다. */
    private void store(long instrumentId, List<DailyPrice> prices) {
        if (prices.isEmpty()) {
            return;
        }
        LocalDate first = prices.getFirst().date();
        LocalDate last = prices.getLast().date();
        Map<LocalDate, BigDecimal> existing = marketData.loadCloses(instrumentId, first, last)
                .map(s -> (Map<LocalDate, BigDecimal>) s.range(first, last))
                .orElse(Map.of());
        List<DailyPrice> inserts = new ArrayList<>();
        List<DailyPrice> updates = new ArrayList<>();
        for (DailyPrice p : prices) {
            BigDecimal old = existing.get(p.date());
            if (old == null) {
                inserts.add(p);
            } else if (old.compareTo(p.close()) != 0) {
                updates.add(p);
            }
        }
        if (!inserts.isEmpty()) {
            marketData.insertDailyPrices(instrumentId, inserts);
        }
        if (!updates.isEmpty()) {
            marketData.upsertDailyPrices(instrumentId, updates);
        }
        if (!inserts.isEmpty() || !updates.isEmpty()) {
            version.bump();
            log.debug("일봉 저장 instrument={} 새 {}일, 정정 {}일", instrumentId, inserts.size(), updates.size());
        }
    }

    static ZoneId zone(CurrencyCode currency) {
        return currency == CurrencyCode.USD ? NEW_YORK : SEOUL;
    }
}
