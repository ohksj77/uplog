package com.uplog.broker.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.uplog.core.CurrencyCode;
import com.uplog.portfolio.broker.BrokerPort;
import com.uplog.portfolio.connect.BrokerAccount;
import com.uplog.portfolio.connect.BrokerCredentials;
import com.uplog.portfolio.connect.BrokerException;
import com.uplog.portfolio.connect.BrokerException.Reason;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossClientTest {

    private static final String BASE = "https://toss.test";
    private static final BrokerCredentials KEY = new BrokerCredentials("client-1", "s3cret-value");
    private static final String TOKEN_OK = """
            {"access_token":"tok-%s","token_type":"Bearer","expires_in":86400}""";
    private static final String ACCOUNTS = """
            {"result":[{"accountNo":"1001-23-456789","accountSeq":7,"accountType":"BROKERAGE"},
                       {"accountNo":"9999-00-000000","accountSeq":8,"accountType":"PENSION_SAVINGS"}]}""";

    private MockRestServiceServer server;
    private FakePacer pacer;
    private TossBrokerAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        pacer = new FakePacer();
        Clock clock = Clock.fixed(Instant.parse("2026-10-16T01:00:00Z"), ZoneOffset.UTC);
        adapter = new TossBrokerAdapter(new TossClient(builder, () -> BASE, TossProperties.defaults(), clock, pacer));
    }

    private void expectToken(String suffix) {
        server.expect(requestTo(BASE + "/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(containsString("grant_type=client_credentials")))
                .andRespond(withSuccess(TOKEN_OK.formatted(suffix), MediaType.APPLICATION_JSON));
    }

    private static String error(String code) {
        return "{\"error\":{\"requestId\":\"r1\",\"code\":\"" + code + "\",\"message\":\"m\"}}";
    }

    @Test
    void 토큰은_clientId별로_캐시해_한_번만_받는다() {
        expectToken("a");
        server.expect(ExpectedCount.times(2), requestTo(BASE + "/api/v1/accounts"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok-a"))
                .andRespond(withSuccess(ACCOUNTS, MediaType.APPLICATION_JSON));

        List<BrokerAccount> first = adapter.verify(KEY);
        adapter.accounts(KEY);

        server.verify();
        assertThat(first).containsExactly(new BrokerAccount("7", "1001-**-****89", "BROKERAGE"));
        // ACCOUNT 그룹은 초당 1건이라 두 번째 호출은 1초를 기다린다
        assertThat(pacer.total()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void token_revoked면_한_번만_다시_받아_재시도한다() {
        expectToken("a");
        server.expect(requestTo(BASE + "/api/v1/accounts"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok-a"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(error("token-revoked")));
        expectToken("b");
        server.expect(requestTo(BASE + "/api/v1/accounts"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok-b"))
                .andRespond(withSuccess(ACCOUNTS, MediaType.APPLICATION_JSON));

        assertThat(adapter.accounts(KEY)).hasSize(1);
        server.verify();
    }

    @Test
    void 다시_받은_토큰도_무효가_되면_TOKEN_REVOKED다() {
        expectToken("a");
        server.expect(requestTo(BASE + "/api/v1/accounts"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(error("token-revoked")));
        expectToken("b");
        server.expect(requestTo(BASE + "/api/v1/accounts"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(error("token-revoked")));

        assertThatThrownBy(() -> adapter.accounts(KEY))
                .isInstanceOfSatisfying(BrokerException.class, e -> assertThat(e.reason()).isEqualTo(Reason.TOKEN_REVOKED))
                .hasMessageNotContaining("s3cret-value")
                .hasMessageNotContaining("tok-");
        server.verify();
    }

    @Test
    void 한도_초과_429면_Retry_After만큼_쉬고_다시_보낸다() {
        expectToken("a");
        HttpHeaders limited = new HttpHeaders();
        limited.set("Retry-After", "3");
        limited.set("X-RateLimit-Remaining", "0");
        server.expect(requestTo(BASE + "/api/v1/accounts"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(limited).contentType(MediaType.APPLICATION_JSON)
                        .body(error("rate-limit-exceeded")));
        server.expect(requestTo(BASE + "/api/v1/accounts")).andRespond(withSuccess(ACCOUNTS, MediaType.APPLICATION_JSON));

        assertThat(adapter.accounts(KEY)).hasSize(1);
        server.verify();
        assertThat(pacer.sleeps).contains(Duration.ofSeconds(3));
    }

    @Test
    void 한도_초과가_계속되면_재시도_뒤_RATE_LIMITED다() {
        expectToken("a");
        HttpHeaders limited = new HttpHeaders();
        limited.set("Retry-After", "1");
        server.expect(ExpectedCount.times(4), requestTo(BASE + "/api/v1/accounts"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(limited).contentType(MediaType.APPLICATION_JSON)
                        .body(error("rate-limit-exceeded")));

        assertThatThrownBy(() -> adapter.accounts(KEY))
                .isInstanceOfSatisfying(BrokerException.class, e -> assertThat(e.reason()).isEqualTo(Reason.RATE_LIMITED));
        server.verify();
    }

    @Test
    void 토큰_발급이_403이면_IP_NOT_ALLOWED다() {
        server.expect(requestTo(BASE + "/oauth2/token"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"access_denied\",\"error_description\":\"IP address not allowed\"}"));

        assertThatThrownBy(() -> adapter.verify(KEY))
                .isInstanceOfSatisfying(BrokerException.class, e -> assertThat(e.reason()).isEqualTo(Reason.IP_NOT_ALLOWED));
    }

    @Test
    void 조회가_403_ip_not_allowed면_IP_NOT_ALLOWED다() {
        expectToken("a");
        server.expect(requestTo(startsWith(BASE + "/api/v1/holdings")))
                .andExpect(header("X-Tossinvest-Account", "7"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body(error("ip-not-allowed")));

        assertThatThrownBy(() -> adapter.holdings(new BrokerPort.AccountRef(KEY, "7")))
                .isInstanceOfSatisfying(BrokerException.class, e -> assertThat(e.reason()).isEqualTo(Reason.IP_NOT_ALLOWED));
    }

    @Test
    void 잘못된_키는_INVALID_CREDENTIALS이고_메시지에_secret이_없다() {
        server.expect(requestTo(BASE + "/oauth2/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_client\",\"error_description\":\"Client authentication failed.\"}"));

        assertThatThrownBy(() -> adapter.verify(KEY))
                .isInstanceOfSatisfying(BrokerException.class, e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_CREDENTIALS))
                .hasMessageNotContaining("s3cret-value");
    }

    @Test
    void 서버_오류가_계속되면_백오프로_재시도한_뒤_UNAVAILABLE이다() {
        expectToken("a");
        server.expect(ExpectedCount.times(4), requestTo(BASE + "/api/v1/accounts"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
                        .body(error("internal-error")));

        assertThatThrownBy(() -> adapter.accounts(KEY))
                .isInstanceOfSatisfying(BrokerException.class, e -> assertThat(e.reason()).isEqualTo(Reason.UNAVAILABLE));
        assertThat(pacer.sleeps).contains(Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    @Test
    void 종합매매_계좌가_없으면_NO_BROKERAGE_ACCOUNT다() {
        expectToken("a");
        server.expect(requestTo(BASE + "/api/v1/accounts")).andRespond(withSuccess("{\"result\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.verify(KEY))
                .isInstanceOfSatisfying(BrokerException.class, e -> assertThat(e.reason()).isEqualTo(Reason.NO_BROKERAGE_ACCOUNT));
    }

    @Test
    void 체결_내역은_체결이_있는_주문만_문자열_숫자를_BigDecimal로_읽고_다음_커서를_준다() {
        expectToken("a");
        server.expect(requestTo(BASE + "/api/v1/orders?status=CLOSED&from=2026-01-01&limit=100"))
                .andExpect(header("X-Tossinvest-Account", "7"))
                .andRespond(withSuccess("""
                        {"result":{"orders":[
                          {"orderId":"o1","symbol":"AAPL","side":"BUY","orderType":"LIMIT","timeInForce":"DAY","status":"CANCELED",
                           "price":"190.00","quantity":"10","orderAmount":null,"currency":"USD",
                           "orderedAt":"2026-03-04T23:40:00+09:00","canceledAt":"2026-03-05T00:10:00+09:00",
                           "execution":{"filledQuantity":"4","averageFilledPrice":"189.5","filledAmount":"758.00",
                             "commission":"0.76","tax":null,"filledAt":"2026-03-04T23:45:00+09:00","settlementDate":"2026-03-06"}},
                          {"orderId":"o2","symbol":"005930","side":"SELL","orderType":"LIMIT","timeInForce":"DAY","status":"CANCELED",
                           "price":"70000","quantity":"3","orderAmount":null,"currency":"KRW",
                           "orderedAt":"2026-03-04T10:00:00+09:00","canceledAt":"2026-03-04T10:05:00+09:00",
                           "execution":{"filledQuantity":"0","averageFilledPrice":null,"filledAmount":null,
                             "commission":null,"tax":null,"filledAt":null,"settlementDate":null}}],
                         "nextCursor":"c100","hasNext":true}}""", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/v1/orders?status=CLOSED&from=2026-01-01&cursor=c100&limit=100"))
                .andRespond(withSuccess("{\"result\":{\"orders\":[],\"nextCursor\":null,\"hasNext\":false}}",
                        MediaType.APPLICATION_JSON));
        BrokerPort.AccountRef ref = new BrokerPort.AccountRef(KEY, "7");

        BrokerPort.OrderPage first = adapter.closedOrders(ref, LocalDate.of(2026, 1, 1), null, null);
        BrokerPort.OrderPage second = adapter.closedOrders(ref, LocalDate.of(2026, 1, 1), null, first.nextCursor());

        assertThat(first.nextCursor()).isEqualTo("c100");
        assertThat(first.orders()).singleElement().satisfies(o -> {
            assertThat(o.orderId()).isEqualTo("o1");
            assertThat(o.market()).isEqualTo(BrokerPort.Market.US);
            assertThat(o.currency()).isEqualTo(CurrencyCode.USD);
            assertThat(o.filledQuantity()).isEqualByComparingTo("4");
            assertThat(o.averageFilledPrice()).isEqualTo(new BigDecimal("189.5"));
            assertThat(o.commission()).isEqualByComparingTo("0.76");
            assertThat(o.tax()).isEqualByComparingTo("0");
        });
        assertThat(second.orders()).isEmpty();
        assertThat(second.nextCursor()).isNull();
        server.verify();
    }

    @Test
    void 일봉의_before는_플러스를_인코딩해_보내고_404면_빈_값이다() {
        expectToken("a");
        server.expect(requestTo(BASE + "/api/v1/candles?symbol=005930&interval=1d&count=200"
                        + "&before=2026-03-25T00%3A00%3A00%2B09%3A00&adjusted=true"))
                .andRespond(withSuccess("""
                        {"result":{"candles":[{"timestamp":"2026-03-25T00:00:00+09:00","openPrice":"1","highPrice":"1",
                          "lowPrice":"1","closePrice":"71200","volume":"10","currency":"KRW"}],"nextBefore":null}}""",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(startsWith(BASE + "/api/v1/candles?symbol=GONE")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(error("stock-not-found")));

        var page = adapter.dailyCandles(KEY, "005930", java.time.OffsetDateTime.parse("2026-03-25T00:00:00+09:00")).orElseThrow();
        assertThat(page.candles()).singleElement()
                .satisfies(c -> assertThat(c.date()).isEqualTo(LocalDate.of(2026, 3, 25)))
                .satisfies(c -> assertThat(c.close()).isEqualByComparingTo("71200"));
        assertThat(page.nextBefore()).isNull();
        assertThat(adapter.dailyCandles(KEY, "GONE", null)).isEmpty();
        server.verify();
    }

    @Test
    void 종목_목록은_ACTIVE만_묻고_상세는_200개씩_나눠_묻는다() {
        expectToken("a");
        server.expect(requestTo(BASE + "/api/v1/stocks/all?market=NASDAQ&status=ACTIVE"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"result":[{"symbol":"AAPL","name":"애플","securityType":"STOCK","isCommonShare":true,"isinCode":"US0378331005"},
                                   {"symbol":"QQQ","name":"인베스코 QQQ","securityType":"FOREIGN_ETF","isCommonShare":true,"isinCode":"x"},
                                   {"symbol":"","name":"빈 코드","securityType":"STOCK","isCommonShare":true,"isinCode":"y"}]}""",
                        MediaType.APPLICATION_JSON));
        java.util.List<String> symbols = new java.util.ArrayList<>();
        for (int i = 0; i < 201; i++) {
            symbols.add("S" + i);
        }
        server.expect(requestTo(startsWith(BASE + "/api/v1/stocks?symbols=S0%2CS1%2C")))
                .andRespond(withSuccess("""
                        {"result":[{"symbol":"S0","name":"영","englishName":"Zero","isinCode":"i","market":"NYSE",
                          "securityType":"STOCK","isCommonShare":true,"status":"ACTIVE","currency":"USD","listDate":"1999-01-04",
                          "delistDate":null,"sharesOutstanding":"1","leverageFactor":null,"koreanMarketDetail":null}]}""",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE + "/api/v1/stocks?symbols=S200"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(error("stock-not-found")));
        var listings = adapter.listings(KEY, com.uplog.portfolio.broker.BrokerPort.ListingMarket.NASDAQ);
        assertThat(listings).extracting(l -> l.symbol() + ":" + l.securityType()).containsExactly("AAPL:STOCK", "QQQ:FOREIGN_ETF");
        var stocks = adapter.stocks(KEY, symbols);
        assertThat(stocks).singleElement().satisfies(s -> {
            assertThat(s.market()).isEqualTo(com.uplog.portfolio.broker.BrokerPort.Market.US);
            assertThat(s.currency()).isEqualTo(com.uplog.core.CurrencyCode.USD);
            assertThat(s.listingMarket()).isEqualTo("NYSE");
            assertThat(s.listDate()).isEqualTo(LocalDate.of(1999, 1, 4));
        });
        server.verify();
    }
}
