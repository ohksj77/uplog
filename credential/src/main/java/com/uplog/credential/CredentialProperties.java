package com.uplog.credential;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code uplog.credential.*}.
 *
 * @param masterKey 마스터키(base64, 32바이트). 환경변수 {@code UPLOG_MASTER_KEY}
 */
@ConfigurationProperties("uplog.credential")
public record CredentialProperties(String masterKey) {

    @Override
    public String toString() {
        return "CredentialProperties[masterKey=***]";
    }
}
