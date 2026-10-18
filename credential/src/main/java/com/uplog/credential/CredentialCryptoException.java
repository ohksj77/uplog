package com.uplog.credential;

import java.io.Serial;

/** 암호화·복호화 실패. 메시지에 키나 평문을 넣지 않는다. */
public final class CredentialCryptoException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CredentialCryptoException(String message) {
        super(message);
    }

    public CredentialCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
