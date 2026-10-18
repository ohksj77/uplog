package com.uplog.ingest.feed;

import com.uplog.marketdata.DailyPrice;
import com.uplog.marketdata.feed.MarketFeed;
import com.uplog.marketdata.feed.MarketFeedException;
import com.uplog.portfolio.broker.BrokerPort;
import com.uplog.portfolio.connect.BrokerCredentials;
import com.uplog.portfolio.connect.BrokerException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 한 사용자의 키로 {@link BrokerPort}를 불러 {@link MarketFeed}로 내놓는다. 증권사 실패는 사용자에게 보여 줄 사유로 바꾼다.
 * 키는 이 객체 밖으로 나가지 않고, 요청 하나 동안만 쓴다.
 */
final class BrokerMarketFeed implements MarketFeed {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 일별 환율은 서울 외환시장 정규 마감 시각 기준(수집 작업과 같다). */
    static final LocalTime FX_TIME = LocalTime.of(15, 30);

    private final BrokerPort broker;
    private final BrokerCredentials credentials;

    BrokerMarketFeed(BrokerPort broker, BrokerCredentials credentials) {
        this.broker = broker;
        this.credentials = credentials;
    }

    @Override
    public List<Listing> listings(ListingMarket market) {
        BrokerPort.ListingMarket m = BrokerPort.ListingMarket.valueOf(market.name());
        return call(() -> broker.listings(credentials, m)).stream()
                .map(l -> new Listing(l.symbol(), l.name(), l.securityType()))
                .toList();
    }

    @Override
    public List<Detail> details(List<String> codes) {
        return call(() -> broker.stocks(credentials, codes)).stream()
                .map(s -> new Detail(s.symbol(), s.name(), s.currency(), s.securityType(), s.status()))
                .toList();
    }

    @Override
    public Optional<CandlePage> dailyCandles(String code, OffsetDateTime before) {
        return call(() -> broker.dailyCandles(credentials, code, before)).map(page -> new CandlePage(
                page.candles().stream().map(c -> DailyPrice.close(c.date(), c.close())).toList(),
                page.nextBefore()));
    }

    @Override
    public Optional<BigDecimal> usdKrw(LocalDate date) {
        return call(() -> broker.usdKrw(credentials, date.atTime(FX_TIME).atZone(SEOUL).toOffsetDateTime()));
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (BrokerException e) {
            throw new MarketFeedException(reason(e.reason()), e);
        }
    }

    static MarketFeedException.Reason reason(BrokerException.Reason reason) {
        return switch (reason) {
            case INVALID_CREDENTIALS, NO_BROKERAGE_ACCOUNT -> MarketFeedException.Reason.INVALID_KEY;
            case IP_NOT_ALLOWED -> MarketFeedException.Reason.IP_NOT_ALLOWED;
            case RATE_LIMITED -> MarketFeedException.Reason.RATE_LIMITED;
            case TOKEN_REVOKED -> MarketFeedException.Reason.KEY_IN_USE_ELSEWHERE;
            case UNAVAILABLE -> MarketFeedException.Reason.UNAVAILABLE;
        };
    }
}
