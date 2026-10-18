package com.uplog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 기능 A 계약(docs/api/simulation.md). 실제 종목만 다루고, 시세는 요청자 자신의 토스 키로 받는다.
 * 가짜 토스 서버(fake-toss)에 연결한 사용자로 검색·시뮬레이션하고, 비로그인·미연결·키 실패를 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:uplog-sim;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
    "uplog.ingest.worker-enabled=false",
    "uplog.toss.rate-limit-multiplier=1000",
    "uplog.toss.fake-server.rate-limit-multiplier=1000"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "api", "fake-toss"})
class SimulationApiTest {

    private static final String DATE = "\\d{4}-\\d{2}-\\d{2}";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Cookie connected;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void connectOnce() throws Exception {
        if (connected == null) {
            connected = connect("시뮬연결", "sim-client");
        }
    }

    private Cookie connect(String nickname, String clientId) throws Exception {
        Cookie session = Sessions.devLogin(mvc, nickname);
        var r = mvc.perform(post("/api/connections/toss").cookie(session).header("Origin", Sessions.WEB_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"clientSecret\":\"secret\"}"))
                .andReturn().getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return session;
    }

    private static MockHttpServletRequestBuilder simulation(String etfs) {
        return get("/api/simulations")
                .param("etfs", etfs)
                .param("period", "1Y")
                .param("frequency", "MONTHLY")
                .param("dayOfMonth", "15")
                .param("amountType", "PER_PURCHASE")
                .param("amount", "300000");
    }

    @Test
    void 비로그인과_미연결_사용자는_검색도_시뮬레이션도_403_SIMULATION_REQUIRES_CONNECTION이다() throws Exception {
        Cookie unconnected = Sessions.devLogin(mvc, "미연결");
        for (Cookie session : new Cookie[] {null, unconnected}) {
            for (MockHttpServletRequestBuilder request : List.of(
                    get("/api/etfs"), get("/api/instruments").param("query", "200"), simulation("069500"))) {
                if (session != null) {
                    request.cookie(session);
                }
                mvc.perform(request)
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code", is("SIMULATION_REQUIRES_CONNECTION")))
                        .andExpect(jsonPath("$.message", containsString("계좌를 연결하면")));
            }
        }
    }

    @Test
    void 연결한_사용자의_검색은_ETF가_주식보다_먼저이고_샘플_종목은_없다() throws Exception {
        mvc.perform(get("/api/etfs").param("query", "200").cookie(connected))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains("069500", "229200", "102110")))
                .andExpect(jsonPath("$[*].type", everyItem(is("ETF"))))
                .andExpect(jsonPath("$[*].market", everyItem(is("KR"))))
                .andExpect(jsonPath("$[*].code", not(hasItem("500001")))); // ETN은 뺀다
        String body = mvc.perform(get("/api/instruments").param("query", "SP").cookie(connected))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code", is("SPY")))
                .andExpect(jsonPath("$[0].market", is("US")))
                .andExpect(jsonPath("$[0].type", is("ETF")))
                .andExpect(jsonPath("$[0].currency", is("USD")))
                .andReturn().getResponse().getContentAsString();
        assertKeys(JSON.readTree(body).get(0), "code", "name", "market", "type", "currency", "firstDate", "lastDate");
        mvc.perform(get("/api/etfs").param("query", "삼성").cookie(connected))
                .andExpect(jsonPath("$[*].code", contains("005930", "005935")))
                .andExpect(jsonPath("$[*].type", everyItem(is("STOCK"))));
        // FOREIGN_ETF도 ETF. 데모 계좌의 샘플 종목(샘플전자, SMPS …)은 나오지 않는다
        mvc.perform(get("/api/etfs").param("query", "VOO").cookie(connected)).andExpect(jsonPath("$[0].type", is("ETF")));
        mvc.perform(get("/api/etfs").param("query", "샘플").cookie(connected)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/etfs").param("query", "SMP").cookie(connected)).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get("/api/etfs").param("limit", "0").cookie(connected))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));
    }

    @Test
    void 국내_ETF와_미국_ETF를_원화로_시뮬레이션한다() throws Exception {
        String body = mvc.perform(simulation("069500,SPY").cookie(connected))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from", matchesPattern(DATE)))
                .andExpect(jsonPath("$.to", matchesPattern(DATE)))
                .andExpect(jsonPath("$.disclaimer", containsString("투자 권유가 아닙니다")))
                .andExpect(jsonPath("$.results[*].etf.code", contains("069500", "SPY")))
                .andExpect(jsonPath("$.results[*].etf.market", contains("KR", "US")))
                .andExpect(jsonPath("$.results[*].etf.type", contains("ETF", "ETF")))
                .andExpect(jsonPath("$.results[*].etf.currency", contains("KRW", "USD")))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = JSON.readTree(body);
        assertKeys(root, "from", "to", "currency", "results", "disclaimer");
        assertThat(root.get("currency").asString()).isEqualTo("KRW");
        for (JsonNode result : root.get("results")) {
            assertKeys(result, "etf", "lumpSum", "recurring");
            assertKeys(result.get("etf"), "code", "name", "market", "type", "currency");
            JsonNode lump = result.get("lumpSum");
            JsonNode rec = result.get("recurring");
            assertThat(lump.get("streaks").get("purchase").isNull()).isTrue();
            assertKeys(rec.get("streaks").get("purchase"), "unit", "longest", "current");
            for (JsonNode sim : List.of(lump, rec)) {
                assertKeys(sim, "summary", "pain", "streaks", "days");
                assertKeys(sim.get("summary"), "finalValue", "invested", "profit", "totalReturn", "twr", "annualizedTwr");
                assertKeys(sim.get("pain"), "maxDrawdown", "maxDrawdownAmount", "underwater", "worstDay", "worstDayAmount");
                String prev = "";
                for (JsonNode day : sim.get("days")) {
                    assertKeys(day, "date", "close", "value", "invested", "dailyReturn", "purchased");
                    assertThat(day.get("value").isIntegralNumber()).isTrue();
                    assertThat(day.get("date").asString()).isGreaterThan(prev);
                    prev = day.get("date").asString();
                }
                assertThat(prev).isEqualTo(root.get("to").asString());
            }
            long purchases = count(rec.get("days"));
            assertThat(purchases).isBetween(11L, 13L);
            assertThat(rec.get("summary").get("invested").asLong()).isEqualTo(300_000 * purchases);
            assertThat(lump.get("summary").get("invested").asLong()).isEqualTo(300_000 * purchases);
        }
        // 가짜 KODEX 200은 수만 원, 가짜 SPY(수백 달러)는 원화로 환산해 수십만 원 이상
        long kodexClose = root.get("results").get(0).get("recurring").get("days").get(0).get("close").asLong();
        long spyClose = root.get("results").get(1).get("recurring").get("days").get(0).get("close").asLong();
        assertThat(kodexClose).isBetween(5_000L, 200_000L);
        assertThat(spyClose).isGreaterThan(200_000L);
        // 환율이 없던 DB라 매수일·종료일 환율만 요청자 키로 받았다(묶음 한도 안)
        long fxDays = jdbc.queryForObject("SELECT COUNT(*) FROM fx_rate WHERE pair = 'USDKRW'", Long.class);
        assertThat(fxDays).isBetween(5L, 40L);
        // 일봉은 기간만큼만 저장했다
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM daily_price p JOIN instrument i ON i.id = p.instrument_id WHERE i.code = 'SPY'""", Long.class))
                .isBetween(250L, 280L);
    }

    @Test
    void currency_USD면_같은_매수를_달러로_나타내고_잘못된_통화는_400이다() throws Exception {
        JsonNode krw = JSON.readTree(mvc.perform(simulation("069500,SPY").cookie(connected))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode usd = JSON.readTree(mvc.perform(simulation("069500,SPY").param("currency", "usd").cookie(connected))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency", is("USD")))
                .andReturn().getResponse().getContentAsString());
        for (int i = 0; i < 2; i++) {
            JsonNode krwDays = krw.get("results").get(i).get("recurring").get("days");
            JsonNode usdDays = usd.get("results").get(i).get("recurring").get("days");
            assertThat(usdDays.size()).isEqualTo(krwDays.size());
            assertThat(count(usdDays)).isEqualTo(count(krwDays));
            // 원화 금액을 1,000원대 환율로 나눈 달러
            double ratio = krwDays.get(0).get("value").asDouble() / usdDays.get(0).get("value").asDouble();
            assertThat(ratio).isBetween(900.0, 2_000.0);
        }
        mvc.perform(simulation("069500").param("currency", "EUR").cookie(connected))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")))
                .andExpect(jsonPath("$.message", containsString("currency")));
    }

    @Test
    void TOTAL은_총액을_회차로_나눠_거치식도_같은_금액을_넣는다() throws Exception {
        String body = mvc.perform(get("/api/simulations").cookie(connected)
                        .param("etfs", "005930")
                        .param("period", "6M")
                        .param("frequency", "WEEKLY")
                        .param("dayOfWeek", "MONDAY")
                        .param("amountType", "TOTAL")
                        .param("amount", "1000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].etf.type", is("STOCK")))
                .andReturn().getResponse().getContentAsString();

        JsonNode result = JSON.readTree(body).get("results").get(0);
        long purchases = count(result.get("recurring").get("days"));
        long invested = result.get("recurring").get("summary").get("invested").asLong();
        assertThat(invested).isEqualTo(1_000_000 / purchases * purchases);
        assertThat(result.get("lumpSum").get("summary").get("invested").asLong()).isEqualTo(invested);
    }

    @Test
    void 잘못된_파라미터는_400이고_없는_종목_샘플_종목_ETN은_404다() throws Exception {
        mvc.perform(get("/api/simulations").cookie(connected)
                        .param("etfs", "069500").param("period", "1Y").param("frequency", "WEEKLY")
                        .param("amountType", "TOTAL").param("amount", "1000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")))
                .andExpect(jsonPath("$.message", containsString("dayOfWeek")));
        mvc.perform(get("/api/simulations").cookie(connected)).andExpect(status().isBadRequest());
        mvc.perform(simulation("069500,A/B").cookie(connected)).andExpect(status().isBadRequest());
        mvc.perform(simulation("A,B,C,D").cookie(connected))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("최대 3개")));
        for (String code : new String[] {"NOPE99", "SMPS", "S00003", "500001", "brk.b"}) {
            mvc.perform(simulation("069500," + code).cookie(connected))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("ETF_NOT_FOUND")))
                    .andExpect(jsonPath("$.message", containsString(code.toUpperCase(Locale.ROOT))));
        }
    }

    @Test
    void 요청자_키로_시세를_받지_못하면_502_MARKET_DATA_UNAVAILABLE이고_다른_데이터로_대신하지_않는다() throws Exception {
        // 계좌 조회는 되지만 시세 조회는 403 ip-not-allowed인 키. 다른 사용자가 받아 둔 시세가 있어도 보여 주지 않는다.
        mvc.perform(simulation("069500").cookie(connected)).andExpect(status().isOk());
        Cookie blocked = connect("막힌키", "market-blocked-client");
        for (MockHttpServletRequestBuilder request : List.of(get("/api/etfs").param("query", "200"), simulation("069500"))) {
            mvc.perform(request.cookie(blocked))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code", is("MARKET_DATA_UNAVAILABLE")))
                    .andExpect(jsonPath("$.message", containsString("IP")));
        }
    }

    private static long count(JsonNode days) {
        long n = 0;
        for (JsonNode day : days) {
            if (day.get("purchased").asBoolean()) {
                n++;
            }
        }
        return n;
    }

    private static void assertKeys(JsonNode node, String... keys) {
        Set<String> actual = new java.util.TreeSet<>();
        for (Iterator<String> it = node.propertyNames().iterator(); it.hasNext(); ) {
            actual.add(it.next());
        }
        assertThat(actual).as(node.toString().substring(0, Math.min(80, node.toString().length())))
                .containsExactlyInAnyOrder(keys);
    }
}
