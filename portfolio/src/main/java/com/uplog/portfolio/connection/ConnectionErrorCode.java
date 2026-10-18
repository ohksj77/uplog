package com.uplog.portfolio.connection;

/** 연결 API 오류 코드(docs/design/account-connection.md). */
public enum ConnectionErrorCode {
    INVALID_PARAMETER(400),
    ACCOUNT_NOT_CONNECTED(404),
    TOSS_INVALID_CREDENTIALS(400),
    /** 본문에 egressIps를 함께 준다 */
    TOSS_IP_NOT_ALLOWED(400),
    TOSS_NO_BROKERAGE_ACCOUNT(400),
    TOSS_RATE_LIMITED(429),
    TOSS_UNAVAILABLE(502),
    ALREADY_CONNECTED(409),
    /** 수동 다시 수집은 계좌당 1시간에 1번 */
    SYNC_TOO_SOON(429);

    private final int httpStatus;

    ConnectionErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
