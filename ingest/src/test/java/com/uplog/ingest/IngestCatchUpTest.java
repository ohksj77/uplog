package com.uplog.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 놓친 날 따라잡기와 한가할 때 폴링 늦추기. */
class IngestCatchUpTest {

    private final IngestTestDatabase db = new IngestTestDatabase();
    private final IngestJobRepository jobs = new IngestJobRepository(db.jdbc, db.tx);
    private final IngestStore store = new IngestStore(db.jdbc, db.tx);
    private final long account = db.insertAccount("1");

    /** 2026-10-21(수) KST 시각 */
    private static Clock kst(String time) {
        return Clock.fixed(Instant.parse("2026-10-21T" + time + ":00+09:00"), ZoneOffset.UTC);
    }

    private IngestCatchUp catchUp(Clock clock) {
        return new IngestCatchUp(store, jobs, new IngestSchedulerService(jobs, store, clock), clock);
    }

    private void done(IngestJob.Kind kind, LocalDate asOf) {
        long id = jobs.enqueue(account, kind, asOf, Instant.parse("2026-10-01T00:00:00Z"));
        jobs.complete(id, "ok", Instant.parse("2026-10-01T00:00:00Z"));
    }

    private List<IngestJob> pendingDaily() {
        return db.jdbc.sql("SELECT id FROM ingest_job WHERE kind = 'DAILY' AND state = 'PENDING'")
                .query(Long.class).list().stream().map(id -> jobs.find(id).orElseThrow()).toList();
    }

    @Test
    void 역산이_끝나지_않은_계좌는_건너뛴다() {
        assertThat(catchUp(kst("09:00")).run()).isZero();
        jobs.enqueue(account, IngestJob.Kind.BACKFILL, null, Instant.now());
        assertThat(catchUp(kst("09:00")).run()).isZero();
        assertThat(pendingDaily()).isEmpty();
    }

    @Test
    void 마지막_성공_뒤로_놓친_거래일이_있으면_가장_최근_거래일_하나로_따라잡는다() {
        done(IngestJob.Kind.BACKFILL, null);
        done(IngestJob.Kind.DAILY, LocalDate.of(2026, 10, 14)); // 수요일 기준분 이후 며칠 꺼져 있었다

        assertThat(catchUp(kst("09:00")).run()).isEqualTo(1);
        assertThat(pendingDaily()).singleElement().satisfies(j -> assertThat(j.asOf()).isEqualTo(LocalDate.of(2026, 10, 20)));

        // 다시 돌려도 같은 작업을 또 넣지 않는다(대기 중)
        catchUp(kst("09:30")).run();
        assertThat(pendingDaily()).hasSize(1);
    }

    @Test
    void 오전_7시_전에는_그_전날의_직전_거래일까지만_본다() {
        done(IngestJob.Kind.BACKFILL, null);
        done(IngestJob.Kind.DAILY, LocalDate.of(2026, 10, 19));

        assertThat(catchUp(kst("06:30")).latestDue()).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(catchUp(kst("06:30")).run()).isZero();
        assertThat(catchUp(kst("07:00")).latestDue()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(catchUp(kst("07:00")).run()).isEqualTo(1);
    }

    @Test
    void 월요일_아침에는_금요일분을_본다_이미_했으면_넣지_않는다() {
        done(IngestJob.Kind.BACKFILL, null);
        done(IngestJob.Kind.DAILY, LocalDate.of(2026, 10, 16));
        Clock monday = Clock.fixed(Instant.parse("2026-10-19T10:00:00+09:00"), ZoneOffset.UTC);
        assertThat(catchUp(monday).latestDue()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(catchUp(monday).run()).isZero();
    }

    @Test
    void 일이_없으면_폴링을_늦추고_작업을_넣으면_바로_깬다() {
        IngestWorkerTest.MutableClock clock = new IngestWorkerTest.MutableClock(Instant.parse("2026-10-21T00:00:00Z"));
        QueueSignal signal = new QueueSignal();
        IngestProperties properties = new IngestProperties(
                true, Duration.ofSeconds(2), Duration.ofSeconds(30), 3, Duration.ofMinutes(1), Duration.ofMinutes(30), 60, true);
        AtomicInteger runs = new AtomicInteger();
        IngestWorker worker = new IngestWorker(jobs, (job, p) -> {
            runs.incrementAndGet();
            return "ok";
        }, store, null, properties, clock, "w1", signal);
        IngestSchedulerService scheduler = new IngestSchedulerService(jobs, store, clock, signal);

        worker.poll(); // 일이 없다 → 30초 쉰다
        // 다른 경로로(신호 없이) 들어온 작업은 쉬는 동안 보지 않는다
        jobs.enqueue(account, IngestJob.Kind.DAILY, LocalDate.of(2026, 10, 19), clock.instant());
        clock.advance(Duration.ofSeconds(10));
        worker.poll();
        assertThat(runs).hasValue(0);

        // 이 엔진이 작업을 넣으면 신호로 바로 깬다
        scheduler.scheduleDailySync(account, LocalDate.of(2026, 10, 20));
        worker.poll();
        assertThat(runs).hasValue(2);

        // 일이 없어 다시 쉬다가, 30초가 지나면 다시 본다(재시도 대기 작업 등)
        worker.poll();
        jobs.enqueue(account, IngestJob.Kind.DAILY, LocalDate.of(2026, 10, 21), clock.instant());
        worker.poll();
        assertThat(runs).hasValue(2);
        clock.advance(Duration.ofSeconds(31));
        worker.poll();
        assertThat(runs).hasValue(3);
    }
}
