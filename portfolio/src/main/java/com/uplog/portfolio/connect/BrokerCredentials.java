package com.uplog.portfolio.connect;

import java.util.Objects;

/** 증권사 Open API 키. 주문 권한까지 가진 키라서 로그·오류·응답 어디에도 secret이 나가면 안 된다. */
public record BrokerCredentials(String clientId, String clientSecret) {

    public BrokerCredentials {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(clientSecret, "clientSecret");
    }

    @Override
    public String toString() {
        return "BrokerCredentials[clientId=" + clientId + ", clientSecret=***]";
    }
}
