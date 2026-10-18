package com.uplog.auth;

import java.io.Serial;

/** 로그인이 필요한 곳에 비로그인으로 왔다. 401 {@code UNAUTHENTICATED}가 된다. */
public final class UnauthenticatedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnauthenticatedException() {
        super(AuthErrors.UNAUTHENTICATED_MESSAGE);
    }
}
