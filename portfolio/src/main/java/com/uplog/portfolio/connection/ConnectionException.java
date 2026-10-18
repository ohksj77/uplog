package com.uplog.portfolio.connection;

import java.io.Serial;
import java.time.Duration;

/** 연결 API 오류. message는 사용자에게 그대로 보여 줄 한국어 문장이고, 키 값은 넣지 않는다. */
public final class ConnectionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ConnectionErrorCode code;
    private final transient Duration retryAfter;

    public ConnectionException(ConnectionErrorCode code, String message) {
        this(code, message, null);
    }

    public ConnectionException(ConnectionErrorCode code, String message, Duration retryAfter) {
        super(message);
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public ConnectionErrorCode code() {
        return code;
    }

    /** 429일 때 다시 시도할 수 있기까지, 모르면 null */
    public Duration retryAfter() {
        return retryAfter;
    }
}
