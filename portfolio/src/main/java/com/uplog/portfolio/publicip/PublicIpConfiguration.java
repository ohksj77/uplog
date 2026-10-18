package com.uplog.portfolio.publicip;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 공인 IP 확인. 다른 {@link PublicIpLookup} 빈(테스트 대역)이 있으면 그것을 쓴다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PublicIpConfiguration.PublicIpProperties.class)
public class PublicIpConfiguration {

    /**
     * {@code uplog.public-ip.*}.
     *
     * @param url 확인 주소(기본 api.ipify.org JSON)
     * @param timeout 연결·응답 시간 제한
     * @param cacheTtl 같은 값을 다시 쓰는 시간(최대 1분)
     */
    @ConfigurationProperties("uplog.public-ip")
    public record PublicIpProperties(
            @DefaultValue("https://api.ipify.org?format=json") URI url,
            @DefaultValue("4s") Duration timeout,
            @DefaultValue("1m") Duration cacheTtl) {}

    @Bean
    PublicIpService publicIpService(ObjectProvider<PublicIpLookup> lookups, PublicIpProperties properties) {
        PublicIpLookup lookup = lookups.getIfAvailable(() -> IpifyLookup.create(properties.url(), properties.timeout()));
        return new PublicIpService(lookup, Clock.systemUTC(), properties.cacheTtl());
    }
}
