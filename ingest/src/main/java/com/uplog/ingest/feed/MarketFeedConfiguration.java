package com.uplog.ingest.feed;

import com.uplog.marketdata.feed.MarketFeeds;
import com.uplog.portfolio.PortfolioRepository;
import com.uplog.portfolio.broker.BrokerPort;
import com.uplog.portfolio.connect.CredentialReader;
import java.time.Clock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 기능 A 실제 종목 시세: 요청자 → 그 사용자의 키로 만든 시세 소스. */
@Configuration(proxyBeanMethods = false)
public class MarketFeedConfiguration {

    @Bean
    MarketFeeds requesterMarketFeeds(
            PortfolioRepository accounts, ObjectProvider<CredentialReader> credentials, BrokerPort broker) {
        return new RequesterMarketFeeds(accounts, credentials::getObject, broker, Clock.systemUTC());
    }
}
