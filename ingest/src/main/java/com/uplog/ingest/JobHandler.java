package com.uplog.ingest;

/** 집힌 작업 하나를 끝까지 돌린다. 실패는 예외로 알린다. */
@FunctionalInterface
public interface JobHandler {

    /** @return 운영용 메모(ingest_job.note) */
    String run(IngestJob job, MarketDataSync.ProgressListener progress);
}
