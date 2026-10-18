package com.uplog.marketdata.publicdata;

import java.io.Serial;

public class PublicDataApiException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PublicDataApiException(String message) {
        super(message);
    }
}
