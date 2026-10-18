package com.uplog.simulation;

public enum ErrorCode {
    INVALID_PARAMETER(400),
    ETF_NOT_FOUND(404),
    NO_PRICE_DATA(422);

    private final int httpStatus;

    ErrorCode(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
