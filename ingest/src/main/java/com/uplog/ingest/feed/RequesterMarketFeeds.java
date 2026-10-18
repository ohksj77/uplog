package com.uplog.ingest.feed;

import com.uplog.marketdata.feed.MarketFeed;
import com.uplog.marketdata.feed.MarketFeedException;
import com.uplog.marketdata.feed.MarketFeeds;
import com.uplog.portfolio.PortfolioRepository;
import com.uplog.portfolio.broker.BrokerPort;
import com.uplog.portfolio.connect.BrokerCredentials;
import com.uplog.portfolio.connect.CredentialReader;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 요청한 사용자 자신의 ACTIVE 계좌 키로만 시세 소스를 만든다. 다른 사용자의 키나 공용 키는 쓰지 않는다.
 * 계좌가 없으면 empty, 계좌는 있는데 키가 없거나 그 키로 시세를 받을 수 없으면 {@link MarketFeedException}.
 *
 * <p>공용 시세 테이블에 이미 있는 값만 쓰는 요청이라도, 요청자의 키가 지금 시세를 받을 수 있는지 {@link #VERIFY_TTL}마다
 * 한 번 가벼운 호출(종목 상세 1건)로 확인한다. 키가 막힌 사용자에게 다른 사용자의 키로 받아 둔 시세를 보여 주지 않기 위해서다.
 */
final class RequesterMarketFeeds implements MarketFeeds {

    static final Duration VERIFY_TTL = Duration.ofMinutes(10);
    /** 확인 호출에 쓰는 종목(어느 키로도 조회되는 대형주) */
    static final String PROBE_SYMBOL = "005930";

    private final PortfolioRepository accounts;
    private final Supplier<CredentialReader> credentials;
    private final BrokerPort broker;
    private final Clock clock;
    private final Map<String, Instant> verified = new ConcurrentHashMap<>();

    RequesterMarketFeeds(PortfolioRepository accounts, Supplier<CredentialReader> credentials, BrokerPort broker, Clock clock) {
        this.accounts = accounts;
        this.credentials = credentials;
        this.broker = broker;
        this.clock = clock;
    }

    @Override
    public Optional<MarketFeed> forUser(long userId) {
        Optional<PortfolioRepository.Account> account = accounts.activeAccountOf(userId);
        if (account.isEmpty()) {
            return Optional.empty();
        }
        BrokerCredentials key;
        try {
            key = credentials.get().read(account.get().id());
        } catch (CredentialReader.CredentialNotFoundException e) {
            throw new MarketFeedException(MarketFeedException.Reason.NO_KEY, e);
        }
        BrokerMarketFeed feed = new BrokerMarketFeed(broker, key);
        String verifiedKey = account.get().id() + ":" + key.clientId();
        Instant now = clock.instant();
        Instant last = verified.get(verifiedKey);
        if (last == null || last.isBefore(now.minus(VERIFY_TTL))) {
            feed.details(List.of(PROBE_SYMBOL)); // 실패하면 사유별 MarketFeedException
            verified.put(verifiedKey, now);
        }
        return Optional.of(feed);
    }
}
