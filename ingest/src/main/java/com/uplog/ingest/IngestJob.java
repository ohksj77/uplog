package com.uplog.ingest;

import java.time.Instant;
import java.time.LocalDate;

/**
 * ingest_job 한 행.
 *
 * @param asOf DAILY의 기준일, BACKFILL은 null
 * @param attempts 집힌 횟수(지금 실행 중인 것 포함)
 */
public record IngestJob(
        long id,
        long accountId,
        Kind kind,
        LocalDate asOf,
        State state,
        int attempts,
        Instant nextRunAt,
        String lastError,
        String errorReason,
        double progress,
        Instant createdAt,
        Instant finishedAt) {

    public enum Kind {
        BACKFILL,
        DAILY
    }

    public enum State {
        PENDING,
        RUNNING,
        DONE,
        FAILED
    }
}
