package com.uplog.broker.toss;

import java.time.LocalTime;

/** 토스 Open API Rate Limits Group과 문서의 초당 한도(docs/toss-openapi-notes.md). AUTH는 문서에 수치가 없어 보수적으로 1. */
public enum RateLimitGroup {
    AUTH(1),
    ACCOUNT(1),
    ASSET(5),
    ORDER_HISTORY(5),
    /** 09:00–09:10(KST)에는 초당 3 */
    ORDER_INFO(6),
    MARKET_INFO(3),
    MARKET_DATA_CHART(20);

    private static final LocalTime OPENING_RUSH_START = LocalTime.of(9, 0);
    private static final LocalTime OPENING_RUSH_END = LocalTime.of(9, 10);

    private final double permitsPerSecond;

    RateLimitGroup(double permitsPerSecond) {
        this.permitsPerSecond = permitsPerSecond;
    }

    /** @param kst 지금 한국 시각 */
    public double permitsPerSecond(LocalTime kst) {
        if (this == ORDER_INFO && !kst.isBefore(OPENING_RUSH_START) && kst.isBefore(OPENING_RUSH_END)) {
            return 3;
        }
        return permitsPerSecond;
    }

    /** 허용 목록에 있는 경로의 그룹. */
    public static RateLimitGroup of(String path) {
        return switch (path) {
            case "/oauth2/token" -> AUTH;
            case "/api/v1/accounts" -> ACCOUNT;
            case "/api/v1/holdings" -> ASSET;
            case "/api/v1/orders" -> ORDER_HISTORY;
            case "/api/v1/buying-power" -> ORDER_INFO;
            case "/api/v1/candles" -> MARKET_DATA_CHART;
            default -> {
                if (path.equals("/api/v1/exchange-rate") || path.startsWith("/api/v1/market-calendar/")) {
                    yield MARKET_INFO;
                }
                throw new IllegalArgumentException("레이트리밋 그룹을 모르는 경로: " + path);
            }
        };
    }
}
