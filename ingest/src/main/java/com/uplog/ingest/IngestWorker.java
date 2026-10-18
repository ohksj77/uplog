package com.uplog.ingest;

import com.uplog.portfolio.PortfolioService;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 큐 워커. 돌릴 때가 된 작업을 하나씩 집어 실행한다. 실패하면 지수 백오프(backoff × 2^(n−1))로 다시 대기시키고,
 * maxAttempts번 실패하거나 다시 해도 소용없는 실패(IP 미등록, 키 무효, 연결 해제)면 FAILED로 둔다.
 * 일일 작업이 끝내 실패하면 그 기준일을 collection_gap에 남겨 로그에 빈 칸으로 보이게 하고, 다음 날 작업은 그대로 돈다.
 */
public class IngestWorker {

    private static final Logger log = LoggerFactory.getLogger(IngestWorker.class);

    private final IngestJobRepository jobs;
    private final JobHandler handler;
    private final IngestStore store;
    private final PortfolioService portfolioService;
    private final IngestProperties properties;
    private final Clock clock;
    private final String workerId;

    public IngestWorker(
            IngestJobRepository jobs, JobHandler handler, IngestStore store, PortfolioService portfolioService,
            IngestProperties properties, Clock clock, String workerId) {
        this.jobs = jobs;
        this.handler = handler;
        this.store = store;
        this.portfolioService = portfolioService;
        this.properties = properties;
        this.clock = clock;
        this.workerId = workerId;
    }

    public static String defaultWorkerId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = "unknown";
        }
        return host + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Scheduled(fixedDelayString = "${uplog.ingest.poll-interval:2s}", initialDelayString = "${uplog.ingest.poll-interval:2s}")
    public void poll() {
        if (properties.workerEnabled()) {
            drain();
        }
    }

    /** 지금 돌릴 수 있는 작업이 없을 때까지 돌린다. @return 실행한 작업 수 */
    public int drain() {
        int n = 0;
        while (runNext()) {
            n++;
        }
        return n;
    }

    /** @return 작업을 하나 집어 돌렸으면 true */
    public boolean runNext() {
        Optional<IngestJob> claimed = jobs.claim(workerId, clock.instant(), properties.lockTimeout());
        if (claimed.isEmpty()) {
            return false;
        }
        execute(claimed.get());
        return true;
    }

    void execute(IngestJob job) {
        try {
            String note = handler.run(job, fraction -> jobs.progress(job.id(), fraction));
            jobs.complete(job.id(), note, clock.instant());
        } catch (RuntimeException e) {
            IngestErrors.Failure failure = IngestErrors.classify(e);
            Instant now = clock.instant();
            if (failure.permanent() || job.attempts() >= properties.maxAttempts()) {
                log.warn("수집 작업 {} ({} 계좌 {}) 실패 확정 [{}] 시도 {}회: {}", job.id(), job.kind(), job.accountId(),
                        failure.reason(), job.attempts(), e.toString(), e);
                jobs.fail(job.id(), failure.reason(), failure.message(), now);
                if (job.kind() == IngestJob.Kind.DAILY && job.asOf() != null
                        && !failure.reason().equals("DISCONNECTED") && !failure.reason().equals("CREDENTIAL_NOT_FOUND")) {
                    recordGap(job, failure);
                }
            } else {
                Duration wait = properties.backoff().multipliedBy(1L << Math.max(0, job.attempts() - 1));
                log.info("수집 작업 {} ({} 계좌 {}) 실패 [{}], {} 뒤 다시 시도: {}", job.id(), job.kind(), job.accountId(),
                        failure.reason(), wait, e.toString());
                jobs.retry(job.id(), now.plus(wait), failure.reason(), failure.message());
            }
        }
    }

    private void recordGap(IngestJob job, IngestErrors.Failure failure) {
        store.insertGap(job.accountId(), job.asOf(), failure.message());
        try {
            portfolioService.refresh(job.accountId());
        } catch (RuntimeException e) {
            log.warn("누락일 기록 뒤 계좌 {} 재계산 실패: {}", job.accountId(), e.toString());
        }
    }
}
