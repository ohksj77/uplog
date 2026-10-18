package com.uplog.broker.toss;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uplog.broker.toss.TossOutboundGuard.BlockedRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossOutboundGuardTest {

    @Test
    void 토큰_발급과_조회용_GET만_허용한다() {
        for (String path : new String[] {"/api/v1/accounts", "/api/v1/holdings", "/api/v1/orders", "/api/v1/buying-power",
            "/api/v1/exchange-rate", "/api/v1/candles", "/api/v1/market-calendar/KR", "/api/v1/market-calendar/US"}) {
            assertThatCode(() -> TossOutboundGuard.check(HttpMethod.GET, path)).doesNotThrowAnyException();
        }
        assertThatCode(() -> TossOutboundGuard.check(HttpMethod.POST, "/oauth2/token")).doesNotThrowAnyException();
    }

    @Test
    void 주문_정정_취소와_목록_밖_경로는_막는다() {
        Object[][] blocked = {
            {HttpMethod.POST, "/api/v1/orders"},
            {HttpMethod.POST, "/api/v1/orders/o1/cancel"},
            {HttpMethod.POST, "/api/v1/orders/o1/modify"},
            {HttpMethod.GET, "/api/v1/orders/o1"},
            {HttpMethod.POST, "/api/v1/conditional-orders"},
            {HttpMethod.DELETE, "/api/v1/conditional-orders/c1"},
            {HttpMethod.GET, "/api/v1/stocks"},
            {HttpMethod.GET, "/oauth2/token"},
            {HttpMethod.PUT, "/api/v1/accounts"},
            {HttpMethod.GET, "/api/v1/candles/../orders"},
            {HttpMethod.GET, "/api/v1/market-calendar/KR/../../orders"},
            {HttpMethod.GET, "/api/v1/%6Frders"},
        };
        for (Object[] b : blocked) {
            assertThatThrownBy(() -> TossOutboundGuard.check((HttpMethod) b[0], (String) b[1]))
                    .as(b[0] + " " + b[1])
                    .isInstanceOf(BlockedRequestException.class);
        }
    }

    @Test
    void 인터셉터는_최종_URI를_보고_보내기_전에_막는다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient http = builder.requestInterceptor(new TossOutboundGuard(() -> "https://toss.test/base")).build();

        assertThatThrownBy(() -> http.post().uri("https://toss.test/base/api/v1/orders").body("{}").retrieve().toBodilessEntity())
                .isInstanceOf(BlockedRequestException.class);
        assertThatThrownBy(() -> http.get().uri("https://evil.test/base/api/v1/accounts").retrieve().toBodilessEntity())
                .isInstanceOf(BlockedRequestException.class);
        assertThatThrownBy(() -> http.get().uri("https://toss.test/other/api/v1/accounts").retrieve().toBodilessEntity())
                .isInstanceOf(BlockedRequestException.class);
        server.verify();
    }
}
