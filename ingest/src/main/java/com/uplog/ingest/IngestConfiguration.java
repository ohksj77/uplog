package com.uplog.ingest;

import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.PriceDataVersion;
import com.uplog.portfolio.PortfolioRepository;
import com.uplog.portfolio.PortfolioService;
import com.uplog.portfolio.broker.BrokerPort;
import com.uplog.portfolio.connect.CredentialReader;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 수집 큐·워커·스케줄러. 워커는 {@code uplog.ingest.worker-enabled}(기본 true)일 때 큐를 폴링한다.
 * 키는 {@link CredentialReader}(credential 모듈)로만 받고, 작업을 돌릴 때 찾는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(IngestProperties.class)
public class IngestConfiguration {

    private final Clock clock = Clock.systemUTC();

    @Bean
    IngestJobRepository ingestJobRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        return new IngestJobRepository(JdbcClient.create(jdbcTemplate), new TransactionTemplate(transactionManager));
    }

    @Bean
    IngestStore ingestStore(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        return new IngestStore(JdbcClient.create(jdbcTemplate), new TransactionTemplate(transactionManager));
    }

    @Bean
    MarketDataSync marketDataSync(BrokerPort broker, MarketDataRepository marketData, PriceDataVersion priceVersion) {
        return new MarketDataSync(broker, marketData, priceVersion);
    }

    @Bean
    IngestRunner ingestRunner(
            BrokerPort broker,
            ObjectProvider<CredentialReader> credentials,
            IngestStore store,
            IngestJobRepository jobs,
            MarketDataSync sync,
            MarketDataRepository marketData,
            PortfolioRepository portfolio,
            PortfolioService portfolioService,
            IngestProperties properties) {
        return new IngestRunner(broker, credentials::getObject, store, jobs, sync, marketData, portfolio, portfolioService,
                properties, clock);
    }

    @Bean
    IngestWorker ingestWorker(
            IngestJobRepository jobs, IngestRunner runner, IngestStore store, PortfolioService portfolioService,
            IngestProperties properties) {
        return new IngestWorker(jobs, runner, store, portfolioService, properties, clock, IngestWorker.defaultWorkerId());
    }

    @Bean
    IngestSchedulerService ingestSchedulerService(IngestJobRepository jobs, IngestStore store) {
        return new IngestSchedulerService(jobs, store, clock);
    }

    @Bean
    IngestDataPurger ingestDataPurger(JdbcTemplate jdbcTemplate) {
        return new IngestDataPurger(JdbcClient.create(jdbcTemplate));
    }

    @Bean
    DailyIngestTrigger dailyIngestTrigger(IngestStore store, IngestSchedulerService scheduler) {
        return new DailyIngestTrigger(store, scheduler, clock);
    }
}
