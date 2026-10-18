package com.uplog.portfolio.connect;

/** 증권사 호출 실패. 메시지에는 키 값을 넣지 않는다. */
public final class BrokerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        INVALID_CREDENTIALS,
        IP_NOT_ALLOWED,
        NO_BROKERAGE_ACCOUNT,
        RATE_LIMITED,
        /** 같은 키로 다른 곳에서 토큰을 새로 받아 우리 토큰이 무효가 됨 */
        TOKEN_REVOKED,
        UNAVAILABLE
    }

    private final Reason reason;

    public BrokerException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public BrokerException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
