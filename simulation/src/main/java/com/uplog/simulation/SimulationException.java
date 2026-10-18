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

    /** 비로그인·미연결: 실제 종목 시세는 요청자 자신의 키로만 받는다. */
    public static SimulationException requiresConnection() {
        return new SimulationException(ErrorCode.SIMULATION_REQUIRES_CONNECTION,
                "실제 종목 시세는 연결한 토스증권 키로 받아요. 계좌를 연결하면 시뮬레이션할 수 있어요.");
    }

    public ErrorCode code() {
        return code;
    }
}
