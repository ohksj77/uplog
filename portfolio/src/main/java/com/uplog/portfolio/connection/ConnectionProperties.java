package com.uplog.portfolio.connection;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code uplog.connection.*}.
 *
 * @param keyPageUrl 연결 안내에서 여는 토스증권 키 발급 화면
 * @param syncInterval 수동 다시 수집 최소 간격(계좌당)
 * @param simulated 진짜 토스 대신 가짜 토스 서버에 연결하는지(fake-toss 프로필). 켜져 있으면 연결한 계좌를
 *     샘플 계좌로 만들고, 연결 화면에 가짜 서버라고 알린다.
 */
@ConfigurationProperties("uplog.connection")
public record ConnectionProperties(
        @DefaultValue("https://www.tossinvest.com/") URI keyPageUrl,
        @DefaultValue("1h") Duration syncInterval,
        @DefaultValue("false") boolean simulated) {}
