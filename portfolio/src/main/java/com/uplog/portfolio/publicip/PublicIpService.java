package com.uplog.portfolio.publicip;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 이 맥의 공인 IP. 요청이 올 때만 확인하고, 연달아 누르는 경우를 위해 최대 1분만 기억한다(그보다 오래 두면 공유기·통신사가
 * IP를 바꿨을 때 옛 IP를 보여 줄 수 있다).
 */
public class PublicIpService {

    static final Duration MAX_CACHE = Duration.ofMinutes(1);

    private final PublicIpLookup lookup;
    private final Clock clock;
    private final Duration ttl;
    private Cached cached;

    public PublicIpService(PublicIpLookup lookup, Clock clock, Duration ttl) {
        this.lookup = lookup;
        this.clock = clock;
        this.ttl = ttl.compareTo(MAX_CACHE) > 0 ? MAX_CACHE : ttl;
    }

    public synchronized String current() {
        Instant now = clock.instant();
        if (cached != null && now.isBefore(cached.at().plus(ttl))) {
            return cached.ip();
        }
        String ip = lookup.lookup();
        cached = new Cached(ip, now);
        return ip;
    }

    private record Cached(String ip, Instant at) {}
}
