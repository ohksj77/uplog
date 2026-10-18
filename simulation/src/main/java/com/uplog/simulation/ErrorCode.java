package com.uplog.simulation;

public enum ErrorCode {
    INVALID_PARAMETER(400),
    ETF_NOT_FOUND(404),
    /** 비로그인이거나 ACTIVE 연결 계좌가 없음. 시세는 요청자 자신의 키로만 받으므로 검색·시뮬레이션 모두 막는다. */
    SIMULATION_REQUIRES_CONNECTION(403),
    NO_PRICE_DATA(422),
    /** 연결된 사용자의 키로 시세를 받지 못함(IP 미등록, 키 무효, 한도 초과 등). 샘플 데이터로 대신하지 않는다. */
    MARKET_DATA_UNAVAILABLE(502);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
