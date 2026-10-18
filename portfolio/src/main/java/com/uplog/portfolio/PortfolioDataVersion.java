package com.uplog.portfolio;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** 이 인스턴스에서 계좌 원시 데이터를 쓸 때마다 올라가는 번호. 계좌 계산 캐시 키에 넣는다(시세는 PriceDataVersion). */
@Component
public final class PortfolioDataVersion {

    private final AtomicLong version = new AtomicLong();

    public long current() {
        return version.get();
    }

    public void bump() {
        version.incrementAndGet();
    }
}
