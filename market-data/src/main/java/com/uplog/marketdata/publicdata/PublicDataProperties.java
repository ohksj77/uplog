package com.uplog.marketdata.publicdata;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code uplog.market-data.public-data.*}. service-key가 비어 있으면 수집 잡을 띄우지 않는다.
 *
 * @param serviceKey 공공데이터포털 "일반 인증키(Decoding)" 값. 쿼리 파라미터로 보낼 때 인코딩한다.
 * @param lookbackDays 매 실행마다 오늘 이전 며칠(평일)을 다시 받는다. 공개가 하루 이상 늦거나 정정되는 경우를 덮는다.
 */
@ConfigurationProperties("uplog.market-data.public-data")
public record PublicDataProperties(
        String serviceKey,
        @DefaultValue("https://apis.data.go.kr/1160100/service/GetSecuritiesProductInfoService") URI baseUrl,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("1000") int pageSize,
        @DefaultValue("7") int lookbackDays) {}
