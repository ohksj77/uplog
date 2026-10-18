package com.uplog.localuser;

import java.io.Serial;

/** 실행 쿠키 없이 사용자가 필요한 곳에 왔다. 403 {@code DESKTOP_LOCKED}가 된다. */
public final class DesktopLockedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DesktopLockedException() {
        super(DesktopErrors.LOCKED_MESSAGE);
    }
}
