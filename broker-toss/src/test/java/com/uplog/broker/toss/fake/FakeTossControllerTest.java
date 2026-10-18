package com.uplog.broker.toss.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 가짜 토스 서버가 공개 명세(1.2.24)의 경로·필드 이름·형식(숫자는 문자열)과 맞는지, 실패 동작이 명세대로인지. */
class FakeTossControllerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-16T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FakeTossController(CLOCK, 1000)).build();

    private MockHttpServletResponse perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    private String token(String clientId, String secret) throws Exception {
        MockHttpServletResponse r = perform(post("/__fake-toss/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials").param("client_id", clientId).param("client_secret", secret));
        assertThat(r.getStatus()).isEqualTo(200);
        JsonNode body = JSON.readTree(r.getContentAsString());
        assertKeys(body, "access_token", "token_type", "expires_in");
        return body.get("access_token").asString();
    }

    private JsonNode result(String path, String token, String accountSeq) throws Exception {
        MockHttpServletRequestBuilder request = get("/__fake-toss" + path).header("Authorization", "Bearer " + token);
        if (accountSeq != null) {
            request.header("X-Tossinvest-Account", accountSeq);
        }
        MockHttpServletResponse r = perform(request);
        assertThat(r.getStatus()).as(path).isEqualTo(200);
        assertThat(r.getHeader("X-RateLimit-Remaining")).isNotNull();
        JsonNode body = JSON.readTree(r.getContentAsString());
        assertKeys(body, "result");
        return body.get("result");
    }

    @Test
    void 계좌_보유_현금_체결_환율_일봉_장운영의_필드는_명세와_같고_숫자는_문자열이다() throws Exception {
        String token = token("client-a", "secret");

        JsonNode accounts = result("/api/v1/accounts", token, null);
        assertThat(accounts).hasSize(1);
        assertKeys(accounts.get(0), "accountNo", "accountSeq", "accountType");

        JsonNode holdings = result("/api/v1/holdings", token, "1");
        assertKeys(holdings, "totalPurchaseAmount", "marketValue", "profitLoss", "dailyProfitLoss", "items");
        assertThat(holdings.get("items")).isNotEmpty();
        for (JsonNode item : holdings.get("items")) {
            assertKeys(item, "symbol", "name", "marketCountry", "currency", "quantity", "lastPrice", "averagePurchasePrice",
                    "marketValue", "profitLoss", "dailyProfitLoss", "cost");
            assertDecimalStrings(item, "quantity", "lastPrice", "averagePurchasePrice");
            assertKeys(item.get("marketValue"), "purchaseAmount", "amount", "amountAfterCost");
        }

        JsonNode krw = result("/api/v1/buying-power?currency=KRW", token, "1");
        assertKeys(krw, "currency", "cashBuyingPower");
        assertDecimalStrings(krw, "cashBuyingPower");

        JsonNode orders = result("/api/v1/orders?status=CLOSED&limit=100", token, "1");
        assertKeys(orders, "orders", "nextCursor", "hasNext");
        assertThat(orders.get("orders")).hasSize(100);
        assertThat(orders.get("hasNext").asBoolean()).isTrue();
        Set<String> statuses = new TreeSet<>();
        for (JsonNode o : orders.get("orders")) {
            assertKeys(o, "orderId", "symbol", "side", "orderType", "timeInForce", "status", "price", "quantity", "orderAmount",
                    "currency", "orderedAt", "canceledAt", "execution");
            assertKeys(o.get("execution"), "filledQuantity", "averageFilledPrice", "filledAmount", "commission", "tax", "filledAt",
                    "settlementDate");
            assertDecimalStrings(o.get("execution"), "filledQuantity");
            statuses.add(o.get("status").asString());
        }
        assertThat(statuses).contains("FILLED", "CANCELED");

        JsonNode fx = result("/api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW&dateTime=2025-06-02T06:30:00Z",
                token, null);
        assertKeys(fx, "baseCurrency", "quoteCurrency", "rate", "midRate", "basisPoint", "rateChangeType", "validFrom", "validUntil");
        assertDecimalStrings(fx, "rate", "midRate");

        JsonNode candles = result("/api/v1/candles?symbol=AAPL&interval=1d&count=200", token, null);
        assertKeys(candles, "candles", "nextBefore");
        assertThat(candles.get("candles")).hasSize(200);
        assertKeys(candles.get("candles").get(0), "timestamp", "openPrice", "highPrice", "lowPrice", "closePrice", "volume", "currency");
        assertDecimalStrings(candles.get("candles").get(0), "closePrice");
        String first = candles.get("candles").get(0).get("timestamp").asString();
        String last = candles.get("candles").get(199).get("timestamp").asString();
        assertThat(first).isGreaterThan(last); // 최신순

        JsonNode kr = result("/api/v1/market-calendar/KR?date=2026-10-17", token, null);
        assertKeys(kr, "today", "previousBusinessDay", "nextBusinessDay");
        assertThat(kr.get("today").get("integrated").isNull()).isTrue(); // 토요일
        assertThat(kr.get("previousBusinessDay").get("date").asString()).isEqualTo("2026-10-16");
    }

    @Test
    void 체결_결과가_보유_수량과_맞는다() throws Exception {
        String token = token("client-b", "secret");
        Map<String, BigDecimal> qty = new java.util.TreeMap<>();
        String cursor = null;
        do {
            JsonNode page = result("/api/v1/orders?status=CLOSED&limit=100" + (cursor == null ? "" : "&cursor=" + cursor), token, "1");
            for (JsonNode o : page.get("orders")) {
                BigDecimal filled = new BigDecimal(o.get("execution").get("filledQuantity").asString());
                qty.merge(o.get("symbol").asString(), o.get("side").asString().equals("BUY") ? filled : filled.negate(),
                        BigDecimal::add);
            }
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
        } while (cursor != null);
        qty.values().removeIf(v -> v.signum() == 0);
        Map<String, BigDecimal> held = new java.util.TreeMap<>();
        for (JsonNode item : result("/api/v1/holdings", token, "1").get("items")) {
            held.put(item.get("symbol").asString(), new BigDecimal(item.get("quantity").asString()));
        }
        assertThat(held).isEqualTo(qty);
    }

    @Test
    void 잘못된_secret은_401_IP_차단은_403_multi는_계좌_2개() throws Exception {
        MockHttpServletResponse invalid = perform(post("/__fake-toss/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials").param("client_id", "x").param("client_secret", "invalid"));
        assertThat(invalid.getStatus()).isEqualTo(401);
        assertThat(JSON.readTree(invalid.getContentAsString()).get("error").asString()).isEqualTo("invalid_client");

        MockHttpServletResponse blocked = perform(post("/__fake-toss/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials").param("client_id", "ip-blocked-1").param("client_secret", "s"));
        assertThat(blocked.getStatus()).isEqualTo(403);
        assertThat(JSON.readTree(blocked.getContentAsString()).get("error").asString()).isEqualTo("access_denied");

        assertThat(result("/api/v1/accounts", token("multi-1", "s"), null)).hasSize(2);
        assertThat(result("/api/v1/accounts", token("no-account-1", "s"), null)).isEmpty();
    }

    @Test
    void 새_토큰을_받으면_이전_토큰은_token_revoked다() throws Exception {
        String old = token("client-c", "s");
        token("client-c", "s");
        MockHttpServletResponse r = perform(get("/__fake-toss/api/v1/accounts").header("Authorization", "Bearer " + old));
        assertThat(r.getStatus()).isEqualTo(401);
        assertThat(JSON.readTree(r.getContentAsString()).get("error").get("code").asString()).isEqualTo("token-revoked");
    }

    @Test
    void 한도를_넘으면_429와_Retry_After를_준다() throws Exception {
        MockMvc strict = MockMvcBuilders.standaloneSetup(new FakeTossController(CLOCK, 1)).build();
        MockHttpServletResponse t = strict.perform(post("/__fake-toss/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials").param("client_id", "c").param("client_secret", "s")).andReturn().getResponse();
        String token = JSON.readTree(t.getContentAsString()).get("access_token").asString();
        int limited = 0;
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse r = strict.perform(get("/__fake-toss/api/v1/accounts").header("Authorization", "Bearer " + token))
                    .andReturn().getResponse();
            if (r.getStatus() == 429) {
                limited++;
                assertThat(r.getHeader("Retry-After")).isEqualTo("1");
                assertThat(r.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
            }
        }
        assertThat(limited).isPositive();
    }

    private static void assertKeys(JsonNode node, String... keys) {
        Set<String> actual = new TreeSet<>();
        for (Iterator<String> it = node.propertyNames().iterator(); it.hasNext(); ) {
            actual.add(it.next());
        }
        assertThat(actual).containsExactlyInAnyOrder(keys);
    }

    private static void assertDecimalStrings(JsonNode node, String... fields) {
        for (String f : fields) {
            assertThat(node.get(f).isString()).as(f).isTrue();
            assertThat(new BigDecimal(node.get(f).asString())).isNotNull();
        }
    }
}
