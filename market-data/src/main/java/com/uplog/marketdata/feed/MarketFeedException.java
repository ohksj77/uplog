package com.uplog.marketdata.feed;

import java.io.Serial;

/** 사용자 키로 시세를 받지 못했다. 메시지는 사용자에게 그대로 보여 줄 한국어 문장이고 키 값을 넣지 않는다. */
public final class MarketFeedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** 계좌는 연결돼 있는데 키가 없다(파기됨) */
        NO_KEY,
        INVALID_KEY,
        IP_NOT_ALLOWED,
        RATE_LIMITED,
        /** 다른 곳에서 같은 키로 토큰을 새로 받았다 */
        KEY_IN_USE_ELSEWHERE,
        UNAVAILABLE
    }

    private final Reason reason;

    public MarketFeedException(Reason reason, Throwable cause) {
        super(message(reason), cause);
        this.reason = reason;
    }

    public MarketFeedException(Reason reason) {
        this(reason, null);
    }

    public Reason reason() {
        return reason;
    }

    static String message(Reason reason) {
        return switch (reason) {
            case NO_KEY -> "연결된 계좌의 토스 키를 찾을 수 없어 시세를 받지 못했어요. 계좌를 다시 연결해 주세요.";
            case INVALID_KEY -> "토스 키가 더 이상 유효하지 않아 시세를 받지 못했어요. 계좌를 다시 연결해 주세요.";
            case IP_NOT_ALLOWED -> "토스 허용 IP에 업로그 서버 IP가 등록돼 있지 않아 시세를 받지 못했어요. 연결 설정에서 IP를 확인해 주세요.";
            case RATE_LIMITED -> "토스 시세 요청이 많아 잠시 막혔어요. 잠시 뒤 다시 시도해 주세요.";
            case KEY_IN_USE_ELSEWHERE -> "다른 곳에서 같은 토스 키를 쓰고 있어 시세를 받지 못했어요. 한 곳에서만 써 주세요.";
            case UNAVAILABLE -> "토스에서 시세를 받지 못했어요. 잠시 뒤 다시 시도해 주세요.";
        };
    }
}
