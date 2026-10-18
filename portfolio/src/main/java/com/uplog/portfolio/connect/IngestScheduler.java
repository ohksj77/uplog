package com.uplog.portfolio.connect;

import java.time.LocalDate;

/** 연결된 계좌의 수집 작업을 큐에 넣고 상태를 알려 준다. */
public interface IngestScheduler {

    /** 연결 직후 1회: 계좌 개설 이후 체결·일봉·환율로 역산 */
    void scheduleBackfill(long accountId);

    /** asOf 기준 일일 수집(수동 다시 수집도 이걸 쓴다) */
    void scheduleDailySync(long accountId, LocalDate asOf);

    IngestStatus status(long accountId);
}
