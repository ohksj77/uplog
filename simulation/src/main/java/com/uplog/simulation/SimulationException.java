package com.uplog.simulation;

import java.io.Serial;

/** 계약의 오류 응답 {@code {code, message}}로 바뀌는 예외. message는 사용자에게 그대로 보여 줄 한국어 문장. */
public final class SimulationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode code;

    public SimulationException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public static SimulationException invalid(String message) {
        return new SimulationException(ErrorCode.INVALID_PARAMETER, message);
    }

    public ErrorCode code() {
        return code;
    }
}
