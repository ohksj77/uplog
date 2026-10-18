package com.uplog.marketdata;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 이 인스턴스에서 시세를 쓸 때마다 올라가는 번호. 시뮬레이션 캐시 키에 넣어 새 시세가 들어오면 무효화한다.
 * 다른 인스턴스가 쓴 시세는 캐시 키의 종목별 마지막 시세일로 잡는다.
 */
@Component
public final class PriceDataVersion {

    private final AtomicLong version = new AtomicLong();

    public long current() {
        return version.get();
    }

    public void bump() {
        version.incrementAndGet();
    }
}
