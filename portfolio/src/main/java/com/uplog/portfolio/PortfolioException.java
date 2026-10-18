package com.uplog.portfolio;

import java.io.Serial;

/** 계약의 오류 응답 {@code {code, message}}로 바뀌는 예외. message는 사용자에게 그대로 보여 줄 한국어 문장. */
public final class PortfolioException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final PortfolioErrorCode code;

    public PortfolioException(PortfolioErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public static PortfolioException invalid(String message) {
        return new PortfolioException(PortfolioErrorCode.INVALID_PARAMETER, message);
    }

    public PortfolioErrorCode code() {
        return code;
    }
}
