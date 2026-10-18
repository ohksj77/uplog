package com.uplog.broker.toss;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code uplog.toss.*}. 기본 주소({@code uplog.toss.base-url}, 기본 https://openapi.tossinvest.com)는 여기 묶지 않고
 * 요청할 때마다 Environment에서 읽는다. local의 가짜 서버 주소가 서버 포트(시작 뒤에 정해짐)를 쓰기 때문이다.
 *
 * @param maxRetries 429·5xx·연결 실패 때 다시 보내는 횟수
 * @param rateLimitMultiplier 그룹별 문서 한도에 곱하는 값. 운영은 1(문서 수치), local 가짜 서버만 키운다.
 * @param lowRemaining {@code X-RateLimit-Remaining}이 이 값 이하이면 그 그룹 요청을 늦춘다
 */
@ConfigurationProperties("uplog.toss")
public record TossProperties(
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("20s") Duration readTimeout,
        @DefaultValue("3") int maxRetries,
        @DefaultValue("1.0") double rateLimitMultiplier,
        @DefaultValue("1") int lowRemaining) {

    public static final String DEFAULT_BASE_URL = "https://openapi.tossinvest.com";

    public static TossProperties defaults() {
        return new TossProperties(Duration.ofSeconds(5), Duration.ofSeconds(20), 3, 1.0, 1);
    }
}
