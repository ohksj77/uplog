package com.uplog.marketdata.feed;

import com.uplog.core.CurrencyCode;
import com.uplog.marketdata.InstrumentType;
import com.uplog.marketdata.ListedInstrument;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.NewInstrument;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 실제 종목 목록(국내 KOSPI·KOSDAQ, 미국 NYSE·NASDAQ·AMEX의 ETF와 주식). 시장마다 하루(KST) 한 번만 증권사에서 받아
 * instrument에 저장한다(sample=false, market KR|US). 목록은 공용 참조 데이터라 저장한 것을 모든 연결 사용자가 검색에 쓰지만,
 * 새로 받을 때는 그 요청을 보낸 사용자의 시세 소스로만 받는다.
 */
@Component
public class InstrumentUniverse {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Logger log = LoggerFactory.getLogger(InstrumentUniverse.class);

    private final FeedRepository repository;
    private final MarketDataRepository marketData;
    private final Clock clock;
    private final Map<MarketFeed.ListingMarket, Object> locks = new EnumMap<>(MarketFeed.ListingMarket.class);

    @Autowired
    public InstrumentUniverse(FeedRepository repository, MarketDataRepository marketData) {
        this(repository, marketData, Clock.system(SEOUL));
    }

    InstrumentUniverse(FeedRepository repository, MarketDataRepository marketData, Clock clock) {
        this.repository = repository;
        this.marketData = marketData;
        this.clock = clock;
        for (MarketFeed.ListingMarket m : MarketFeed.ListingMarket.values()) {
            locks.put(m, new Object());
        }
    }

    /** 오늘(KST) 아직 받지 않은 시장만 받는다. @return 이번에 받은 시장 수 */
    public int ensureFresh(MarketFeed feed) {
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        int synced = 0;
        for (MarketFeed.ListingMarket market : MarketFeed.ListingMarket.values()) {
            synchronized (locks.get(market)) {
                if (repository.universeSyncedOn(market).filter(today::equals).isPresent()) {
                    continue;
                }
                sync(feed, market, today);
                synced++;
            }
        }
        return synced;
    }

    private void sync(MarketFeed feed, MarketFeed.ListingMarket market, LocalDate today) {
        List<NewInstrument> instruments = new ArrayList<>();
        for (MarketFeed.Listing l : feed.listings(market)) {
            Optional<InstrumentType> type = SecurityTypes.classify(l.securityType());
            if (type.isPresent()) {
                instruments.add(new NewInstrument(l.code(), l.name(), country(market.currency()), market.currency(), type.get(), false));
            }
        }
        int changed = repository.upsertRealInstruments(instruments, market, today);
        log.info("종목 목록 {}: {}종(새로 넣거나 고친 것 {})", market, instruments.size(), changed);
    }

    /**
     * 코드로 종목을 찾고, 없으면 증권사 상세로 확인해 넣는다(목록에 없는 시장의 종목, 오늘 상장 등).
     * ETF·주식이 아니거나 증권사도 모르면 empty. 같은 코드의 샘플 종목이 있으면 그대로 돌려준다(쓰는 쪽이 거른다).
     */
    public Optional<ListedInstrument> findOrFetch(MarketFeed feed, String code) {
        Optional<ListedInstrument> found = marketData.findByCode(code);
        if (found.isPresent()) {
            return found;
        }
        for (MarketFeed.Detail d : feed.details(List.of(code))) {
            Optional<InstrumentType> type = SecurityTypes.classify(d.securityType());
            if (d.code().equals(code) && type.isPresent() && !"DELISTED".equals(d.status())) {
                repository.upsertRealInstruments(
                        List.of(new NewInstrument(code, d.name(), country(d.currency()), d.currency(), type.get(), false)),
                        null, null);
                return marketData.findByCode(code);
            }
        }
        return Optional.empty();
    }

    /** KR | US. 화면·API의 시장 구분은 통화로 정한다. */
    public static String country(CurrencyCode currency) {
        return currency == CurrencyCode.USD ? "US" : "KR";
    }
}
