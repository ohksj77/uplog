package com.uplog.credential;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code uplog.credential.*}.
 *
 * @param masterKey 마스터키를 직접 넣는다(base64, 32바이트). 개발·테스트용이고, 넣으면 키체인·키 파일을 쓰지 않는다
 * @param keyStore 마스터키 보관 위치: {@code auto}(macOS면 키체인, 아니면 파일), {@code keychain}, {@code file}
 * @param keyFile {@code file}일 때의 키 파일(기본 {@code {data-dir}/master.key}, 권한 600)
 */
@ConfigurationProperties("uplog.credential")
public record CredentialProperties(String masterKey, @DefaultValue("auto") KeyStore keyStore, String keyFile) {

    public enum KeyStore {
        AUTO,
        KEYCHAIN,
        FILE
    }

    @Override
    public String toString() {
        return "CredentialProperties[masterKey=***, keyStore=" + keyStore + ", keyFile=" + keyFile + "]";
    }
}
