package com.uplog.app;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** local 프로필(H2 + 샘플 ETF 시드)로 띄워 docs/api/simulation.md의 JSON 형태를 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "api"})
class SimulationApiTest {

    private static final String DATE = "\\d{4}-\\d{2}-\\d{2}";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    @Test
    void ETF_목록은_이름순이고_샘플_표시와_시세_구간을_준다() throws Exception {
        // 기능 A용 SAMPLE01–04 + 기능 B 데모용 원화 ETF S00003(달러 ETF SMPS는 원화가 아니라 빠진다)
        mvc.perform(get("/api/etfs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[*].code", everyItem(matchesPattern("SAMPLE0[1-4]|S00003"))))
                .andExpect(jsonPath("$[*].name", everyItem(matchesPattern("샘플 .*"))))
                .andExpect(jsonPath("$[*].currency", everyItem(is("KRW"))))
                .andExpect(jsonPath("$[*].sample", everyItem(is(true))))
                .andExpect(jsonPath("$[0].firstDate", matchesPattern(DATE)))
                .andExpect(jsonPath("$[0].lastDate", matchesPattern(DATE)));

        mvc.perform(get("/api/etfs").param("query", "sample02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains("SAMPLE02")));
        mvc.perform(get("/api/etfs").param("query", "배당").param("limit", "1"))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(get("/api/etfs").param("limit", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));
    }

    @Test
    void 시뮬레이션은_ETF마다_거치식과_적립식을_같은_총액으로_돌려준다() throws Exception {
        String body = mvc.perform(get("/api/simulations")
                        .param("etfs", "SAMPLE01,SAMPLE02")
                        .param("period", "1Y")
                        .param("frequency", "MONTHLY")
                        .param("dayOfMonth", "15")
                        .param("amountType", "PER_PURCHASE")
                        .param("amount", "300000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from", matchesPattern(DATE)))
                .andExpect(jsonPath("$.to", matchesPattern(DATE)))
                .andExpect(jsonPath("$.disclaimer", containsString("투자 권유가 아닙니다")))
                .andExpect(jsonPath("$.results[*].etf.code", contains("SAMPLE01", "SAMPLE02")))
                .andExpect(jsonPath("$.results[0].etf.sample", is(true)))
                .andExpect(jsonPath("$.results[0].etf.currency", is("KRW")))
                .andExpect(jsonPath("$.results[0].lumpSum.pain.maxDrawdown.peak", matchesPattern(DATE)))
                .andExpect(jsonPath("$.results[0].recurring.pain.worstDay.date", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        JsonNode root = JSON.readTree(body);
        assertKeys(root, "from", "to", "results", "disclaimer");
        for (JsonNode result : root.get("results")) {
            assertKeys(result, "etf", "lumpSum", "recurring");
            assertKeys(result.get("etf"), "code", "name", "currency", "sample");
            JsonNode lump = result.get("lumpSum");
            JsonNode rec = result.get("recurring");
            org.assertj.core.api.Assertions.assertThat(lump.get("streaks").get("purchase").isNull()).isTrue();
            assertKeys(rec.get("streaks").get("purchase"), "unit", "longest", "current");
            for (JsonNode sim : List.of(lump, rec)) {
                assertKeys(sim, "summary", "pain", "streaks", "days");
                assertKeys(sim.get("streaks"), "gain", "loss", "purchase");
                assertKeys(sim.get("streaks").get("gain"), "longest", "current");
                assertKeys(sim.get("streaks").get("gain").get("longest"), "length", "start", "end");
                assertKeys(sim.get("summary"), "finalValue", "invested", "profit", "totalReturn", "twr", "annualizedTwr");
                assertKeys(sim.get("pain"), "maxDrawdown", "maxDrawdownAmount", "underwater", "worstDay", "worstDayAmount");
                assertKeys(sim.get("pain").get("maxDrawdownAmount"), "amount", "peak", "trough");
                assertKeys(sim.get("pain").get("worstDayAmount"), "date", "pnl");
                org.assertj.core.api.Assertions.assertThat(sim.get("pain").get("maxDrawdownAmount").get("amount").isIntegralNumber())
                        .isTrue();
                assertKeys(sim.get("pain").get("maxDrawdown"), "rate", "peak", "trough");
                assertKeys(sim.get("pain").get("worstDay"), "date", "dailyReturn");
                JsonNode underwater = sim.get("pain").get("underwater");
                if (!underwater.isNull()) {
                    assertKeys(underwater, "start", "end", "recovered", "days");
                }
                for (String money : List.of("finalValue", "invested", "profit")) {
                    org.assertj.core.api.Assertions.assertThat(sim.get("summary").get(money).isIntegralNumber())
                            .as(money).isTrue();
                }
                String prev = "";
                for (JsonNode day : sim.get("days")) {
                    assertKeys(day, "date", "close", "value", "invested", "dailyReturn", "purchased");
                    org.assertj.core.api.Assertions.assertThat(day.get("value").isIntegralNumber()).isTrue();
                    org.assertj.core.api.Assertions.assertThat(day.get("date").asString()).isGreaterThan(prev);
                    prev = day.get("date").asString();
                }
                org.assertj.core.api.Assertions.assertThat(sim.get("days").get(sim.get("days").size() - 1).get("date").asString())
                        .isEqualTo(root.get("to").asString());
            }
            // PER_PURCHASE: 거치식 = 회차당 × 회차 수 = 적립식 총 투입금
            long purchases = count(rec.get("days"));
            org.assertj.core.api.Assertions.assertThat(purchases).isBetween(11L, 13L);
            org.assertj.core.api.Assertions.assertThat(rec.get("summary").get("invested").asLong()).isEqualTo(300_000 * purchases);
            org.assertj.core.api.Assertions.assertThat(lump.get("summary").get("invested").asLong()).isEqualTo(300_000 * purchases);
            org.assertj.core.api.Assertions.assertThat(count(lump.get("days"))).isEqualTo(1);
        }
    }

    @Test
    void TOTAL은_총액을_회차로_나눠_거치식도_같은_금액을_넣는다() throws Exception {
        String body = mvc.perform(get("/api/simulations")
                        .param("etfs", "SAMPLE03")
                        .param("period", "6M")
                        .param("frequency", "WEEKLY")
                        .param("dayOfWeek", "MONDAY")
                        .param("amountType", "TOTAL")
                        .param("amount", "1000000"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode result = JSON.readTree(body).get("results").get(0);
        long purchases = count(result.get("recurring").get("days"));
        long invested = result.get("recurring").get("summary").get("invested").asLong();
        org.assertj.core.api.Assertions.assertThat(invested).isEqualTo(1_000_000 / purchases * purchases);
        org.assertj.core.api.Assertions.assertThat(result.get("lumpSum").get("summary").get("invested").asLong()).isEqualTo(invested);
    }

    @Test
    void 잘못된_파라미터는_400_INVALID_PARAMETER다() throws Exception {
        mvc.perform(get("/api/simulations")
                        .param("etfs", "SAMPLE01")
                        .param("period", "1Y")
                        .param("frequency", "WEEKLY")
                        .param("amountType", "TOTAL")
                        .param("amount", "1000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")))
                .andExpect(jsonPath("$.message", containsString("dayOfWeek")));
        mvc.perform(get("/api/simulations"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));
        mvc.perform(get("/api/simulations")
                        .param("etfs", "SAMPLE01").param("period", "1Y").param("frequency", "DAILY")
                        .param("amountType", "TOTAL").param("amount", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("amount")));
    }

    @Test
    void 없는_ETF는_404_ETF_NOT_FOUND다() throws Exception {
        mvc.perform(get("/api/simulations")
                        .param("etfs", "SAMPLE01,NOPE99")
                        .param("period", "1Y")
                        .param("frequency", "DAILY")
                        .param("amountType", "TOTAL")
                        .param("amount", "1000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ETF_NOT_FOUND")))
                .andExpect(jsonPath("$.message", containsString("NOPE99")));
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
        org.assertj.core.api.Assertions.assertThat(actual).as(node.toString().substring(0, Math.min(80, node.toString().length())))
                .containsExactlyInAnyOrder(keys);
    }
}
