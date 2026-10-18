package com.uplog.marketdata.publicdata;

import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.PriceDataVersion;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** service-key가 설정됐을 때만 공공데이터 클라이언트와 일별 수집 잡을 띄운다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("!'${uplog.market-data.public-data.service-key:}'.isBlank()")
@EnableConfigurationProperties(PublicDataProperties.class)
@EnableScheduling
public class PublicDataConfiguration {

    @Bean
    PublicDataEtfPriceClient publicDataEtfPriceClient(PublicDataProperties properties) {
        return PublicDataEtfPriceClient.create(properties);
    }

    @Bean
    EtfPriceIngestJob etfPriceIngestJob(
            PublicDataEtfPriceClient client,
            MarketDataRepository repository,
            PriceDataVersion version,
            PublicDataProperties properties) {
        return new EtfPriceIngestJob(client, repository, version, Clock.system(ZoneId.of("Asia/Seoul")), properties.lookbackDays());
    }
}
