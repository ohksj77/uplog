package com.uplog.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class IngestJobRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-16T22:00:00Z");
    private static final Duration LOCK = Duration.ofMinutes(30);

    private final IngestTestDatabase db = new IngestTestDatabase();
    private final IngestJobRepository jobs = new IngestJobRepository(db.jdbc, db.tx);
    private final long account = db.insertAccount("1");

    @Test
    void 같은_계좌_종류_기준일의_대기_작업은_한_번만_넣는다() {
        long a = jobs.enqueue(account, IngestJob.Kind.BACKFILL, null, NOW);
        long b = jobs.enqueue(account, IngestJob.Kind.BACKFILL, null, NOW);
        long c = jobs.enqueue(account, IngestJob.Kind.DAILY, LocalDate.of(2026, 10, 16), NOW);
        long d = jobs.enqueue(account, IngestJob.Kind.DAILY, LocalDate.of(2026, 10, 16), NOW);
        long e = jobs.enqueue(account, IngestJob.Kind.DAILY, LocalDate.of(2026, 10, 15), NOW);
        assertThat(a).isEqualTo(b);
        assertThat(c).isEqualTo(d).isNotEqualTo(e);
    }

    @Test
    void 집으면_RUNNING이_되고_시도_횟수가_오르며_때가_안_된_작업은_집지_않는다() {
        long id = jobs.enqueue(account, IngestJob.Kind.BACKFILL, null, NOW);
        assertThat(jobs.claim("w1", NOW.minusSeconds(1), LOCK)).isEmpty();
        IngestJob job = jobs.claim("w1", NOW, LOCK).orElseThrow();
        assertThat(job.id()).isEqualTo(id);
        assertThat(job.state()).isEqualTo(IngestJob.State.RUNNING);
        assertThat(job.attempts()).isEqualTo(1);
        assertThat(jobs.claim("w2", NOW, LOCK)).isEmpty();
        // 워커가 죽어 오래 RUNNING이면 다시 집는다
        assertThat(jobs.claim("w2", NOW.plus(LOCK).plusSeconds(1), LOCK)).map(IngestJob::attempts).contains(2);
    }

    @Test
    void 워커_둘이_동시에_집어도_같은_작업을_두_번_돌리지_않는다() throws Exception {
        int n = 40;
        for (int i = 0; i < n; i++) {
            jobs.enqueue(account, IngestJob.Kind.DAILY, LocalDate.of(2026, 1, 1).plusDays(i), NOW);
        }
        ConcurrentLinkedQueue<Long> claimed = new ConcurrentLinkedQueue<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> workers = List.of("w1", "w2").stream().map(w -> pool.submit(() -> {
                start.await();
                int mine = 0;
                for (Optional<IngestJob> j; (j = jobs.claim(w, NOW, LOCK)).isPresent(); ) {
                    claimed.add(j.get().id());
                    jobs.complete(j.get().id(), w, NOW);
                    mine++;
                }
                return mine;
            })).toList();
            start.countDown();
            int total = 0;
            for (Future<Integer> f : workers) {
                total += f.get(30, TimeUnit.SECONDS);
            }
            assertThat(total).isEqualTo(n);
        } finally {
            pool.shutdownNow();
        }
        assertThat(claimed).hasSize(n).doesNotHaveDuplicates();
        assertThat(db.jdbc.sql("SELECT COUNT(*) FROM ingest_job WHERE state = 'DONE' AND attempts = 1").query(Long.class).single())
                .isEqualTo(n);
    }

    @Test
    void SKIP_LOCKED는_다른_트랜잭션이_잠근_행을_건너뛴다() throws Exception {
        long first = jobs.enqueue(account, IngestJob.Kind.DAILY, LocalDate.of(2026, 10, 15), NOW.minusSeconds(10));
        long second = jobs.enqueue(account, IngestJob.Kind.DAILY, LocalDate.of(2026, 10, 16), NOW);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> db.tx.executeWithoutResult(s -> {
                db.jdbc.sql("SELECT id FROM ingest_job WHERE id = :id FOR UPDATE").param("id", first).query(Long.class).single();
                locked.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(jobs.claim("w2", NOW, LOCK)).map(IngestJob::id).contains(second);
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }
}
