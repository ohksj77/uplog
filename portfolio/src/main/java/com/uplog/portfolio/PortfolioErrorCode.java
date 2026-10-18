package com.uplog.portfolio;

public enum PortfolioErrorCode {
    INVALID_PARAMETER(400),
    /** 연결된 계좌가 없다(예: 데모 시드가 없는 기본 프로필) */
    ACCOUNT_NOT_CONNECTED(404),
    INSTRUMENT_NOT_FOUND(404),
    /** 거래일이 아니거나 기록이 없다(MISSING 포함) */
    DATE_NOT_FOUND(404),
    /** 연결 직후 역산 중이라 시세가 아직 다 들어오지 않았다 */
    ACCOUNT_NOT_READY(409);

    private final int httpStatus;

    PortfolioErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
