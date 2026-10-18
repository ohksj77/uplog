package com.uplog.broker.toss;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TossRateLimiterTest {

    private static final Clock NOON_KST = Clock.fixed(Instant.parse("2026-10-16T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void 버킷_크기만큼은_바로_보내고_그_뒤는_한도_간격으로_기다린다() {
        FakePacer pacer = new FakePacer();
        TossRateLimiter limiter = new TossRateLimiter(pacer, NOON_KST, 1.0, 0);
        for (int i = 0; i < 5; i++) {
            limiter.acquire(RateLimitGroup.ASSET);
        }
        assertThat(pacer.sleeps).isEmpty();
        limiter.acquire(RateLimitGroup.ASSET);
        assertThat(pacer.total()).isEqualTo(Duration.ofMillis(200));
        // 다른 그룹은 따로 센다
        limiter.acquire(RateLimitGroup.ACCOUNT);
        assertThat(pacer.total()).isEqualTo(Duration.ofMillis(200));
    }

    @Test
    void 남은_한도가_0이면_Reset만큼_멈춘다() {
        FakePacer pacer = new FakePacer();
        TossRateLimiter limiter = new TossRateLimiter(pacer, NOON_KST, 1.0, 1);
        limiter.observe(RateLimitGroup.MARKET_DATA_CHART, 0, 2);
        limiter.acquire(RateLimitGroup.MARKET_DATA_CHART);
        assertThat(pacer.total()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void 주문_정보_그룹은_장_시작_10분간_초당_3이다() {
        assertThat(RateLimitGroup.ORDER_INFO.permitsPerSecond(LocalTime.of(9, 5))).isEqualTo(3);
        assertThat(RateLimitGroup.ORDER_INFO.permitsPerSecond(LocalTime.of(9, 10))).isEqualTo(6);
        assertThat(RateLimitGroup.of("/api/v1/market-calendar/US")).isEqualTo(RateLimitGroup.MARKET_INFO);
    }

    @Test
    void 계좌번호는_앞_4자리와_끝_2자리만_보인다() {
        assertThat(AccountNumbers.mask("1001-23-456789")).isEqualTo("1001-**-****89");
        assertThat(AccountNumbers.mask("12345678901")).isEqualTo("1234*****01");
    }
}
