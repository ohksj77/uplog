package com.uplog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.uplog.ingest.IngestWorker;
import com.uplog.portfolio.widget.WidgetSnapshotWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 위젯 스냅샷 파일: 연결 전에는 데모 계좌, 가짜 토스 계좌를 연결하면 그 계좌. 형식은 docs/design/desktop-app.md "위젯". */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "uplog.test.context=WidgetSnapshotTest",
    "uplog.desktop.data-dir=${java.io.tmpdir}/uplog-widget-test-${random.uuid}",
    "uplog.ingest.worker-enabled=false",
    "uplog.widget.check-interval=1h", // 테스트가 직접 write()를 부른다
    "uplog.toss.rate-limit-multiplier=1000",
    "uplog.toss.fake-server.rate-limit-multiplier=1000"
})
@AutoConfigureMockMvc
@ActiveProfiles({"desktop", "fake-toss"})
class WidgetSnapshotTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SECRET = "widget-secret-value";
    private static final String CLIENT_ID = "widget-client-id";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private IngestWorker worker;
    @Autowired
    private WidgetSnapshotWriter writer;

    private JsonNode written() throws Exception {
        assertThat(writer.write()).isTrue();
        Path file = writer.file().orElseThrow();
        assertThat(file.getFileName().toString()).isEqualTo("widget.json");
        assertThat(file.getParent().getFileName().toString()).isEqualTo("widget");
        String text = Files.readString(file);
        assertThat(text).doesNotContain(SECRET, CLIENT_ID, "clientSecret", "token");
        return JSON.readTree(text);
    }

    @Test
    void 연결_전에는_데모_계좌를_연결하면_내_계좌를_쓴다() throws Exception {
        LocalAccount.reset(mvc);
        JsonNode demo = written();
        assertShape(demo);
        assertThat(demo.get("accountName").asString()).isEqualTo("데모 계좌");
        assertThat(demo.get("sample").asBoolean()).isTrue();
        assertThat(demo.get("today").isNull()).isFalse();
        // 데모 계좌는 매일 기록이 있어 최근 26주에 색칠된 칸이 많다.
        long colored = cells(demo).stream().filter(c -> !c.get("level").isNull()).count();
        assertThat(colored).isGreaterThan(50);

        var connect = mvc.perform(post("/api/connections/toss").header("Origin", LocalAccount.DEV_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + CLIENT_ID + "\",\"clientSecret\":\"" + SECRET + "\"}"))
                .andReturn().getResponse();
        assertThat(connect.getStatus()).as(connect.getContentAsString()).isEqualTo(201);
        assertThat(worker.drain()).isEqualTo(2);

        JsonNode mine = written();
        assertShape(mine);
        assertThat(mine.get("accountName").asString()).isEqualTo("가짜 토스 계좌(개발용)");
        assertThat(mine.toString()).doesNotContain("데모 계좌");

        LocalAccount.reset(mvc);
        assertThat(written().get("accountName").asString()).isEqualTo("데모 계좌");
    }

    private static List<JsonNode> cells(JsonNode s) {
        List<JsonNode> cells = new ArrayList<>();
        s.get("weeks").forEach(w -> w.get("days").forEach(cells::add));
        return cells;
    }

    private static void assertShape(JsonNode s) {
        assertThat(s.propertyNames()).containsExactlyInAnyOrder("version", "generatedAt", "asOf", "accountName", "sample", "today",
                "month", "year", "streak", "weeks", "thresholds");
        assertThat(s.get("version").asInt()).isEqualTo(1);
        assertThat(s.get("generatedAt").asString()).endsWith("Z");
        LocalDate asOf = LocalDate.parse(s.get("asOf").asString());
        assertThat(s.get("today").propertyNames()).containsExactlyInAnyOrder("pnl", "return");
        assertThat(s.get("month").propertyNames()).containsExactlyInAnyOrder("pnl", "gainDays", "lossDays");
        assertThat(s.get("year").propertyNames()).containsExactlyInAnyOrder("pnl", "gainDays", "lossDays");
        assertThat(s.get("year").get("gainDays").asInt()).isGreaterThanOrEqualTo(s.get("month").get("gainDays").asInt());
        assertThat(s.get("streak").propertyNames()).containsExactlyInAnyOrder("kind", "length");
        assertThat(s.get("thresholds")).hasSize(3);
        JsonNode weeks = s.get("weeks");
        assertThat(weeks).hasSize(26);
        LocalDate lastMonday = LocalDate.parse(weeks.get(25).get("start").asString());
        assertThat(lastMonday.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(asOf).isBeforeOrEqualTo(lastMonday.plusDays(4));
        for (JsonNode w : weeks) {
            assertThat(w.get("days")).hasSize(5);
            for (JsonNode d : w.get("days")) {
                assertThat(d.propertyNames()).containsExactlyInAnyOrder("date", "level", "pnl");
                assertThat(d.get("level").isNull()).isEqualTo(d.get("pnl").isNull());
                if (!d.get("level").isNull()) {
                    assertThat(d.get("level").asInt()).isBetween(-4, 4);
                    assertThat(Long.signum(d.get("pnl").asLong())).isEqualTo(Integer.signum(d.get("level").asInt()));
                }
            }
        }
    }
}
