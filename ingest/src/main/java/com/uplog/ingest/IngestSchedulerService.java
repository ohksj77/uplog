package com.uplog.ingest;

import com.uplog.portfolio.connect.IngestScheduler;
import com.uplog.portfolio.connect.IngestStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** {@link IngestScheduler} 구현. 작업은 큐에 넣기만 하고, 워커가 집어 돌린다. */
public class IngestSchedulerService implements IngestScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int GAP_WINDOW_DAYS = 30;

    private final IngestJobRepository jobs;
    private final IngestStore store;
    private final Clock clock;
    private final QueueSignal signal;

    public IngestSchedulerService(IngestJobRepository jobs, IngestStore store, Clock clock) {
        this(jobs, store, clock, new QueueSignal());
    }

    public IngestSchedulerService(IngestJobRepository jobs, IngestStore store, Clock clock, QueueSignal signal) {
        this.jobs = jobs;
        this.store = store;
        this.clock = clock;
        this.signal = signal;
    }

    @Override
    public void scheduleBackfill(long accountId) {
        jobs.enqueue(accountId, IngestJob.Kind.BACKFILL, null, clock.instant());
        signal.wake();
    }

    @Override
    public void scheduleDailySync(long accountId, LocalDate asOf) {
        jobs.enqueue(accountId, IngestJob.Kind.DAILY, asOf, clock.instant());
        signal.wake();
    }

    /**
     * backfillState: 역산 작업이 없으면 PENDING, 재시도 대기 중이면 RUNNING으로 보여 준다.
     * lastError: 가장 최근에 끝났거나 실패한 작업이 실패였으면 그 사유(성공한 작업이 뒤에 있으면 null).
     */
    @Override
    public IngestStatus status(long accountId) {
        var backfill = jobs.latest(accountId, IngestJob.Kind.BACKFILL);
        String state = backfill
                .map(j -> j.state() == IngestJob.State.PENDING && j.attempts() > 0 ? "RUNNING" : j.state().name())
                .orElse("PENDING");
        double progress = backfill.map(j -> j.state() == IngestJob.State.DONE ? 1.0 : j.progress()).orElse(0.0);
        String lastError = jobs.latestSettled(accountId)
                .filter(j -> j.state() != IngestJob.State.DONE)
                .map(IngestJob::lastError)
                .orElse(null);
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        return new IngestStatus(state, progress, jobs.lastDailyDone(accountId).orElse(null), lastError,
                store.gapsSince(accountId, today.minusDays(GAP_WINDOW_DAYS)));
    }
}
