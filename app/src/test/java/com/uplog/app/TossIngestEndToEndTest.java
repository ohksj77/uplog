package com.uplog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.uplog.ingest.IngestWorker;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 끝에서 끝까지: 가짜 토스 키로 연결(실제 TossBrokerAdapter가 HTTP로 가짜 토스 서버를 부른다)
 * → 역산 + 첫 일일 수집 → 연결 상태 ACTIVE, 내 계좌 로그·요약이 가짜 계좌의 체결로 그려진다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "uplog.test.context=TossIngestEndToEndTest", // 클래스마다 따로 컨텍스트(새 메모리 DB)
    "uplog.ingest.worker-enabled=false",
    "uplog.toss.rate-limit-multiplier=1000",
    "uplog.toss.fake-server.rate-limit-multiplier=1000"
})
@AutoConfigureMockMvc
@ActiveProfiles({"desktop", "fake-toss"})
class TossIngestEndToEndTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private IngestWorker worker;
    @Autowired
    private JdbcTemplate jdbc;

    private JsonNode fetch(String url) throws Exception {
        var r = mvc.perform(get(url)).andReturn().getResponse();
        assertThat(r.getStatus()).as(url + " " + r.getContentAsString()).isEqualTo(200);
        return JSON.readTree(r.getContentAsString());
    }

    @Test
    void 가짜_토스_계좌를_연결하면_역산과_일일_수집_뒤_내_계좌_로그가_보인다() throws Exception {
        LocalAccount.reset(mvc);
        assertThat(fetch("/api/portfolio/summary").get("account").get("name").asString()).isEqualTo("데모 계좌");
        var connect = mvc.perform(post("/api/connections/toss").header("Origin", LocalAccount.DEV_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"e2e-client\",\"clientSecret\":\"e2e-secret\"}"))
                .andReturn().getResponse();
        assertThat(connect.getStatus()).as(connect.getContentAsString()).isEqualTo(201);
        assertThat(fetch("/api/connections/toss").get("status").asString()).isEqualTo("BACKFILLING");

        assertThat(worker.drain()).isEqualTo(2); // 역산, 그 뒤 예약된 첫 일일 수집

        JsonNode status = fetch("/api/connections/toss");
        assertThat(status.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(status.get("backfill").get("state").asString()).isEqualTo("DONE");
        assertThat(status.get("lastError").isNull()).isTrue();
        assertThat(status.get("lastSyncedAt").isNull()).isFalse();

        long accountId = jdbc.queryForObject("SELECT id FROM account WHERE broker_account_seq = '1' AND user_id IS NOT NULL", Long.class);
        long trades = jdbc.queryForObject("SELECT COUNT(*) FROM trade WHERE account_id = ?", Long.class, accountId);
        assertThat(trades).isGreaterThan(80);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT instrument_id) FROM trade WHERE account_id = ?", Long.class, accountId))
                .isEqualTo(8L);
        // 미국 종목 체결은 체결 시점 환율, 국내는 1
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM trade t JOIN instrument i ON i.id = t.instrument_id
                WHERE t.account_id = ? AND i.currency = 'USD' AND t.fx_rate < 1000""", Long.class, accountId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fx_rate WHERE pair = 'USDKRW' AND date >= DATE '2024-10-14'", Long.class))
                .isGreaterThan(400);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM daily_price p JOIN instrument i ON i.id = p.instrument_id WHERE i.code = '005930'""", Long.class))
                .isGreaterThan(450);
        assertThat(jdbc.queryForObject("SELECT name FROM instrument WHERE code = '005930'", String.class)).isEqualTo("삼성전자");

        // 일일 수집: 보유·현금 스냅샷, 추정 유입, 보유 대사 일치
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM holding_snapshot WHERE account_id = ?", Long.class, accountId)).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cash_snapshot WHERE account_id = ?", Long.class, accountId)).isEqualTo(1L);
        List<String> notes = jdbc.queryForList("SELECT note FROM ingest_job WHERE account_id = ? ORDER BY id", String.class, accountId);
        assertThat(notes).hasSize(2);
        assertThat(notes.get(1)).startsWith("일일").doesNotContain("불일치");
        BigDecimal inferred = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN type = 'DEPOSIT' THEN amount ELSE -amount END), 0)
                FROM cash_flow WHERE account_id = ? AND source = 'INFERRED'""", BigDecimal.class, accountId);
        assertThat(inferred).isPositive();

        JsonNode summary = fetch("/api/portfolio/summary");
        // 가짜 토스 서버에서 온 계좌는 실제 계좌로 보이지 않게 샘플 계좌로 만든다
        assertThat(summary.get("account").get("sample").asBoolean()).isTrue();
        assertThat(summary.get("account").get("name").asString()).isEqualTo("가짜 토스 계좌(개발용)");
        assertThat(summary.get("totalValue").asLong()).isPositive();
        assertThat(summary.get("netDeposits").asLong()).isPositive();

        JsonNode log = fetch("/api/portfolio/log");
        assertThat(log.get("availableYears").size()).isGreaterThanOrEqualTo(2);
        assertThat(log.get("days")).isNotEmpty();
        for (JsonNode day : log.get("days")) {
            assertThat(day.get("value").asLong()).as(day.toString()).isPositive();
        }
        assertThat(log.get("days").get(0).get("source").asString()).isEqualTo("BACKFILLED");
        assertThat(fetch("/api/portfolio/holdings")).isNotEmpty();
    }
}
