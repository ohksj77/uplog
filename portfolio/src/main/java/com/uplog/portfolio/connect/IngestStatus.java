package com.uplog.portfolio.connect;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 계좌 수집 상태.
 *
 * @param backfillState PENDING | RUNNING | DONE | FAILED
 * @param backfillProgress 0.0–1.0
 * @param lastSyncedAt 마지막 일일 수집 성공 시각, 없으면 null
 * @param lastError 사용자에게 보여 줄 마지막 실패 사유, 없으면 null
 * @param recentGaps 최근 30일 안의 수집 누락일
 */
public record IngestStatus(
        String backfillState, double backfillProgress, Instant lastSyncedAt, String lastError, List<LocalDate> recentGaps) {

    public IngestStatus {
        recentGaps = List.copyOf(recentGaps);
    }
}
