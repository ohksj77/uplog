package com.uplog.marketdata.feed;

import java.util.Optional;

/** 요청한 사용자 → 그 사용자 자신의 키로 만든 시세 소스. */
public interface MarketFeeds {

    /**
     * 연결된(ACTIVE) 계좌가 없으면 empty. 계좌는 있는데 키를 쓸 수 없으면 {@link MarketFeedException}을 던진다
     * (샘플 데이터로 조용히 넘어가지 않게).
     */
    Optional<MarketFeed> forUser(long userId);
}
