package com.uplog.broker.toss;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;

/**
 * 그룹별 토큰 버킷(인메모리, 워커 1대 기준). 버킷 크기는 초당 한도와 같다(문서의 burst capacity).
 * 응답 헤더 {@code X-RateLimit-Remaining}이 낮으면 그 그룹을 잠시 멈춰 서버 쪽 버킷이 다시 차게 둔다.
 * 워커가 여러 대가 되면 Redis로 옮긴다.
 */
public final class TossRateLimiter {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final Map<RateLimitGroup, Bucket> buckets = new EnumMap<>(RateLimitGroup.class);
    private final Pacer pacer;
    private final Clock clock;
    private final double multiplier;
    private final int lowRemaining;

    public TossRateLimiter(Pacer pacer, Clock clock, double multiplier, int lowRemaining) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException("rateLimitMultiplier는 양수여야 합니다: " + multiplier);
        }
        this.pacer = pacer;
        this.clock = clock;
        this.multiplier = multiplier;
        this.lowRemaining = lowRemaining;
        for (RateLimitGroup g : RateLimitGroup.values()) {
            buckets.put(g, new Bucket(pacer.nanoTime(), rate(g)));
        }
    }

    double rate(RateLimitGroup group) {
        return group.permitsPerSecond(LocalTime.now(clock.withZone(SEOUL))) * multiplier;
    }

    /** 그 그룹에 요청 하나를 보낼 수 있을 때까지 기다린다. */
    public void acquire(RateLimitGroup group) {
        long waitNanos = buckets.get(group).reserve(pacer.nanoTime(), rate(group));
        if (waitNanos > 0) {
            pacer.sleep(Duration.ofNanos(waitNanos));
        }
    }

    /** 그 그룹을 duration 동안 멈춘다(429 Retry-After, 남은 한도 0). */
    public void pause(RateLimitGroup group, Duration duration) {
        buckets.get(group).pauseUntil(pacer.nanoTime() + duration.toNanos());
    }

    /**
     * 응답의 한도 헤더를 반영한다. 남은 수가 0이면 Reset(없으면 1초)만큼, lowRemaining 이하이면 한 칸 간격만큼 늦춘다.
     *
     * @param remaining {@code X-RateLimit-Remaining}, 없으면 null
     * @param resetSeconds {@code X-RateLimit-Reset}, 없으면 null
     */
    public void observe(RateLimitGroup group, Integer remaining, Integer resetSeconds) {
        if (remaining == null) {
            return;
        }
        if (remaining <= 0) {
            pause(group, Duration.ofSeconds(resetSeconds == null || resetSeconds <= 0 ? 1 : resetSeconds));
        } else if (remaining <= lowRemaining) {
            pause(group, Duration.ofNanos((long) (1_000_000_000L / rate(group))));
        }
    }

    private static final class Bucket {

        private double tokens;
        private long lastNanos;
        private long pausedUntilNanos;

        Bucket(long now, double capacity) {
            this.tokens = Math.max(1, capacity);
            this.lastNanos = now;
            this.pausedUntilNanos = now;
        }

        /** 토큰 하나를 미리 떼고 기다릴 시간을 돌려준다(음수 잔고 = 줄 선 요청). */
        synchronized long reserve(long now, double rate) {
            double capacity = Math.max(1, rate);
            tokens = Math.min(capacity, tokens + (now - lastNanos) * rate / 1e9);
            lastNanos = now;
            tokens -= 1;
            long tokenWait = tokens >= 0 ? 0 : (long) Math.ceil(-tokens / rate * 1e9);
            long pauseWait = Math.max(0, pausedUntilNanos - now);
            return Math.max(tokenWait, pauseWait);
        }

        synchronized void pauseUntil(long nanos) {
            pausedUntilNanos = Math.max(pausedUntilNanos, nanos);
        }
    }
}
