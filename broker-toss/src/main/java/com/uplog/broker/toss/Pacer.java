package com.uplog.broker.toss;

import java.time.Duration;

/** 시간과 대기. 테스트에서 실제로 잠들지 않게 바꿔 끼운다. */
public interface Pacer {

    long nanoTime();

    void sleep(Duration duration);

    static Pacer system() {
        return new Pacer() {
            @Override
            public long nanoTime() {
                return System.nanoTime();
            }

            @Override
            public void sleep(Duration duration) {
                if (duration.isZero() || duration.isNegative()) {
                    return;
                }
                try {
                    Thread.sleep(duration);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("토스 요청 대기 중 중단됨", e);
                }
            }
        };
    }
}
