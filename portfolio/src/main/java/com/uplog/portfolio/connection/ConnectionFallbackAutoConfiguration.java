package com.uplog.portfolio.connection;

import com.uplog.portfolio.connect.BrokerException;
import com.uplog.portfolio.connect.BrokerVerifier;
import com.uplog.portfolio.connect.IngestScheduler;
import com.uplog.portfolio.connect.IngestStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * broker-toss·ingest 모듈이 없을 때의 대체 빈. 자동 구성이라 사용자 빈(@Component·@Bean)을 다 등록한 뒤에 평가되므로,
 * 실제 구현이 있으면 물러난다.
 */
@AutoConfiguration
public class ConnectionFallbackAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(BrokerVerifier.class)
    BrokerVerifier unavailableBrokerVerifier() {
        return credentials -> {
            throw new BrokerException(BrokerException.Reason.UNAVAILABLE, "증권사 연동 모듈이 없습니다");
        };
    }

    @Bean
    @ConditionalOnMissingBean(IngestScheduler.class)
    IngestScheduler noopIngestScheduler() {
        return new IngestScheduler() {
            @Override
            public void scheduleBackfill(long accountId) {}

            @Override
            public void scheduleDailySync(long accountId, LocalDate asOf) {}

            @Override
            public IngestStatus status(long accountId) {
                return new IngestStatus("PENDING", 0.0, null, null, List.of());
            }
        };
    }
}
