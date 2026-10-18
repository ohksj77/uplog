package com.uplog.portfolio.connection;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code uplog.egress-ips}: 토스를 호출하는 우리 서버의 고정 IP. 사용자가 토스 WTS의 허용 IP에 등록해야 한다.
 * 환경변수 {@code UPLOG_EGRESS_IPS}(쉼표로 구분).
 */
@ConfigurationProperties("uplog")
public record EgressProperties(@DefaultValue List<String> egressIps) {

    public EgressProperties {
        egressIps = egressIps.stream().map(String::strip).filter(ip -> !ip.isEmpty()).toList();
    }
}
