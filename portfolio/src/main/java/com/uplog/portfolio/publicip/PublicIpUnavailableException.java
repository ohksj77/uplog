package com.uplog.portfolio.publicip;

import java.io.Serial;

/** 공인 IP를 확인하지 못했다(인터넷 끊김, 시간 초과, 이상한 응답). 502 {@code PUBLIC_IP_UNAVAILABLE}. */
public final class PublicIpUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PublicIpUnavailableException(String message) {
        super(message);
    }

    public PublicIpUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
