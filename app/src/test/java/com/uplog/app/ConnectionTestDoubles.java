package com.uplog.app;

import com.uplog.portfolio.connect.BrokerAccount;
import com.uplog.portfolio.connect.BrokerCredentials;
import com.uplog.portfolio.connect.BrokerException;
import com.uplog.portfolio.connect.BrokerVerifier;
import com.uplog.portfolio.connect.IngestScheduler;
import com.uplog.portfolio.connect.IngestStatus;
import com.uplog.portfolio.connection.AccountDataPurger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 연결 테스트용 대역. 실제 구현(broker-toss·ingest)이 함께 떠도 이쪽이 쓰이게 @Primary로 둔다. */
@TestConfiguration(proxyBeanMethods = false)
class ConnectionTestDoubles {

    /** 다음 verify가 돌려줄 계좌 목록이나 던질 사유. 마지막으로 받은 키를 기억한다. */
    static final class StubBrokerVerifier implements BrokerVerifier {
        volatile List<BrokerAccount> accounts = List.of(new BrokerAccount("1", "1234-**-**56", "BROKERAGE"));
        volatile BrokerException.Reason failure;
        volatile BrokerCredentials lastCredentials;

        void reset() {
            accounts = List.of(new BrokerAccount("1", "1234-**-**56", "BROKERAGE"));
            failure = null;
            lastCredentials = null;
        }

        @Override
        public List<BrokerAccount> verify(BrokerCredentials credentials) {
            lastCredentials = credentials;
            if (failure != null) {
                throw new BrokerException(failure, "stub: " + failure);
            }
            return accounts;
        }
    }

    record Call(String kind, long accountId, LocalDate asOf) {}

    static final class RecordingIngestScheduler implements IngestScheduler {
        final List<Call> calls = new CopyOnWriteArrayList<>();
        volatile IngestStatus status = new IngestStatus("RUNNING", 0.42, null, null, List.of());

        void reset() {
            calls.clear();
            status = new IngestStatus("RUNNING", 0.42, null, null, List.of());
        }

        @Override
        public void scheduleBackfill(long accountId) {
            calls.add(new Call("BACKFILL", accountId, null));
        }

        @Override
        public void scheduleDailySync(long accountId, LocalDate asOf) {
            calls.add(new Call("DAILY", accountId, asOf));
        }

        @Override
        public IngestStatus status(long accountId) {
            return status;
        }
    }

    static final class RecordingPurger implements AccountDataPurger {
        final List<Long> purged = new CopyOnWriteArrayList<>();

        @Override
        public void purge(long accountId) {
            purged.add(accountId);
        }
    }

    static final Instant SYNCED_AT = Instant.parse("2026-10-16T22:05:00Z");

    @Bean
    @Primary
    StubBrokerVerifier stubBrokerVerifier() {
        return new StubBrokerVerifier();
    }

    @Bean
    @Primary
    RecordingIngestScheduler recordingIngestScheduler() {
        return new RecordingIngestScheduler();
    }

    @Bean
    RecordingPurger recordingPurger() {
        return new RecordingPurger();
    }
}
