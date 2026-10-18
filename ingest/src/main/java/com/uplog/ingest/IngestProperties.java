package com.uplog.ingest;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code uplog.ingest.*}.
 *
 * @param workerEnabled 이 인스턴스가 큐를 폴링해 작업을 돌릴지
 * @param pollInterval 큐 폴링 간격
 * @param maxAttempts 이만큼 실패하면 FAILED(일일 작업은 collection_gap 기록)
 * @param backoff 재시도 간격의 기준. n번째 실패 뒤 backoff × 2^(n−1)
 * @param lockTimeout RUNNING인 채 이보다 오래된 작업은 워커가 죽은 것으로 보고 다시 집는다
 * @param candleLookback 첫 체결 몇 거래일 전부터 일봉을 받을지(종목 차트용)
 * @param inferBackfillDeposits 역산 구간에서 매수 대금이 모자라는 날 그만큼 입금(INFERRED)이 있었다고 볼지
 */
@ConfigurationProperties("uplog.ingest")
public record IngestProperties(
        @DefaultValue("true") boolean workerEnabled,
        @DefaultValue("2s") Duration pollInterval,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("1m") Duration backoff,
        @DefaultValue("30m") Duration lockTimeout,
        @DefaultValue("60") int candleLookback,
        @DefaultValue("true") boolean inferBackfillDeposits) {

    public static IngestProperties defaults() {
        return new IngestProperties(true, Duration.ofSeconds(2), 3, Duration.ofMinutes(1), Duration.ofMinutes(30), 60, true);
    }
}
