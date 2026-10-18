package com.uplog.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.uplog.portfolio.connect.BrokerException;
import com.uplog.portfolio.connect.IngestStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 실패 → 지수 백오프 재시도 → 3번째 실패에 FAILED + 누락일, 상태(lastError) 표시. */
class IngestWorkerTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 16);

    private final IngestTestDatabase db = new IngestTestDatabase();
    private final IngestJobRepository jobs = new IngestJobRepository(db.jdbc, db.tx);
    private final IngestStore store = new IngestStore(db.jdbc, db.tx);
    private final long account = db.insertAccount("1");
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-16T22:00:00Z"));
    private final IngestProperties properties =
            new IngestProperties(false, Duration.ofSeconds(2), 3, Duration.ofMinutes(1), Duration.ofMinutes(30), 60, true);
    private final IngestSchedulerService scheduler = new IngestSchedulerService(jobs, store, clock);

    private IngestWorker worker(JobHandler handler) {
        // PortfolioService는 누락일 기록 뒤 재계산에만 쓰고 그 실패는 삼킨다
        return new IngestWorker(jobs, handler, store, null, properties, clock, "w1");
    }

    @Test
    void 실패하면_1분_2분_간격으로_다시_하고_세_번째_실패에_FAILED와_누락일을_남긴다() {
        AtomicInteger calls = new AtomicInteger();
        IngestWorker worker = worker((job, p) -> {
            calls.incrementAndGet();
            throw new BrokerException(BrokerException.Reason.UNAVAILABLE, "down");
        });
        scheduler.scheduleDailySync(account, AS_OF);

        assertThat(worker.drain()).isEqualTo(1);
        IngestJob job = jobs.latest(account, null).orElseThrow();
        assertThat(job.state()).isEqualTo(IngestJob.State.PENDING);
        assertThat(job.nextRunAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(1)));
        assertThat(worker.drain()).isZero(); // 아직 때가 안 됐다

        clock.advance(Duration.ofMinutes(1));
        assertThat(worker.drain()).isEqualTo(1);
        assertThat(jobs.latest(account, null).orElseThrow().nextRunAt()).isEqualTo(clock.instant().plus(Duration.ofMinutes(2)));
        assertThat(scheduler.status(account).lastError()).contains("토스증권 응답이 원활하지 않아요");

        clock.advance(Duration.ofMinutes(2));
        assertThatRecordsGap(worker);
        assertThat(calls).hasValue(3);
    }

    private void assertThatRecordsGap(IngestWorker worker) {
        worker.drain(); // 재계산(PortfolioService)이 없어 실패하지만 누락일 기록은 남고 예외는 삼킨다
        IngestJob job = jobs.latest(account, null).orElseThrow();
        assertThat(job.state()).isEqualTo(IngestJob.State.FAILED);
        assertThat(job.attempts()).isEqualTo(3);
        IngestStatus status = scheduler.status(account);
        assertThat(status.recentGaps()).containsExactly(AS_OF);
        assertThat(status.lastError()).isNotNull();
    }

    @Test
    void IP_미등록은_다시_하지_않고_바로_실패하며_IP_안내를_남긴다() {
        IngestWorker worker = worker((job, p) -> {
            throw new BrokerException(BrokerException.Reason.IP_NOT_ALLOWED, "403");
        });
        scheduler.scheduleBackfill(account);
        worker.drain();
        IngestStatus status = scheduler.status(account);
        assertThat(status.backfillState()).isEqualTo("FAILED");
        assertThat(status.lastError()).contains("IP", "허용");
    }

    @Test
    void 키가_다른_곳에서_쓰이면_안내를_남기고_성공하면_지운다() {
        AtomicInteger calls = new AtomicInteger();
        IngestWorker worker = worker((job, p) -> {
            if (calls.incrementAndGet() == 1) {
                throw new BrokerException(BrokerException.Reason.TOKEN_REVOKED, "revoked");
            }
            p.report(0.5);
            return "ok";
        });
        scheduler.scheduleBackfill(account);
        worker.drain();
        IngestStatus retrying = scheduler.status(account);
        assertThat(retrying.backfillState()).isEqualTo("RUNNING");
        assertThat(retrying.lastError()).contains("다른 곳에서 같은 키를 사용 중");

        clock.advance(Duration.ofMinutes(1));
        worker.drain();
        IngestStatus done = scheduler.status(account);
        assertThat(done.backfillState()).isEqualTo("DONE");
        assertThat(done.backfillProgress()).isEqualTo(1.0);
        assertThat(done.lastError()).isNull();
    }

    @Test
    void 연결이_해제된_계좌의_일일_작업은_누락일을_남기지_않는다() {
        IngestWorker worker = worker((job, p) -> {
            throw new IngestErrors.AccountDisconnectedException(job.accountId());
        });
        scheduler.scheduleDailySync(account, AS_OF);
        worker.drain();
        assertThat(jobs.latest(account, null).orElseThrow().state()).isEqualTo(IngestJob.State.FAILED);
        assertThat(store.gapsSince(account, AS_OF.minusDays(30))).isEmpty();
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
