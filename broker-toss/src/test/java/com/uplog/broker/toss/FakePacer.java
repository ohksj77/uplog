package com.uplog.broker.toss;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** 잠들지 않고 시간만 앞으로 돌리는 Pacer. 잔 시간을 기록한다. */
final class FakePacer implements Pacer {

    final List<Duration> sleeps = new ArrayList<>();
    private long now = 1_000_000_000L;

    @Override
    public synchronized long nanoTime() {
        return now;
    }

    @Override
    public synchronized void sleep(Duration duration) {
        sleeps.add(duration);
        now += duration.toNanos();
    }

    synchronized void advance(Duration d) {
        now += d.toNanos();
    }

    synchronized Duration total() {
        return sleeps.stream().reduce(Duration.ZERO, Duration::plus);
    }
}
