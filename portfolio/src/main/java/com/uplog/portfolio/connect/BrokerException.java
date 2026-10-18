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
    private final String field;

    public BrokerException(Reason reason, String message) {
        this(reason, message, (String) null);
    }

    /**
     * @param field 키 인증 실패에서 어느 값이 틀렸는지("clientId" | "clientSecret"), 모르면 null. 토스는 error_description에
     *     어느 쪽 검증이 실패했는지 적어 준다.
     */
    public BrokerException(Reason reason, String message, String field) {
        super(message);
        this.reason = reason;
        this.field = field;
    }

    public BrokerException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.field = null;
    }

    public Reason reason() {
        return reason;
    }

    /** INVALID_CREDENTIALS에서 틀린 값("clientId" | "clientSecret"), 모르면 null */
    public String field() {
        return field;
    }
}
