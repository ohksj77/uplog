package com.uplog.broker.toss;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.function.Supplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** 토스 클라이언트와 어댑터(BrokerPort·BrokerVerifier 구현). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TossProperties.class)
public class TossConfiguration {

    @Bean
    TossClient tossClient(TossProperties properties, Environment environment) {
        // 요청 때마다 읽는다: local의 가짜 서버 주소가 시작 뒤에 정해지는 local.server.port를 쓴다.
        Supplier<String> baseUrl = () -> environment.getProperty("uplog.toss.base-url", TossProperties.DEFAULT_BASE_URL);
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(properties.readTimeout());
        return new TossClient(RestClient.builder().requestFactory(factory), baseUrl, properties, Clock.systemUTC(), Pacer.system());
    }

    @Bean
    TossBrokerAdapter tossBrokerAdapter(TossClient client) {
        return new TossBrokerAdapter(client);
    }
}
