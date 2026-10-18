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
 */
@ConfigurationProperties("uplog.connection")
public record ConnectionProperties(
        @DefaultValue("https://tossinvest.com") URI keyPageUrl,
        @DefaultValue("1h") Duration syncInterval) {}
