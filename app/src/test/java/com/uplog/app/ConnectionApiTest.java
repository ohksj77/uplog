package com.uplog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.uplog.app.ConnectionTestDoubles.Call;
import com.uplog.app.ConnectionTestDoubles.RecordingIngestScheduler;
import com.uplog.app.ConnectionTestDoubles.RecordingPurger;
import com.uplog.app.ConnectionTestDoubles.StubBrokerVerifier;
import com.uplog.credential.BrokerCredentialVault;
import com.uplog.portfolio.PortfolioRepository;
import com.uplog.portfolio.PortfolioService;
import com.uplog.portfolio.connect.BrokerAccount;
import com.uplog.portfolio.connect.BrokerException.Reason;
import com.uplog.portfolio.connect.CredentialReader;
import com.uplog.portfolio.connect.IngestStatus;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 토스 계좌 연결 흐름(가짜 검증기·수집 예약기). 키(secret)는 어떤 응답·로그에도 나오면 안 된다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "api"})
@TestPropertySource(properties = "uplog.egress-ips=127.0.0.1")
@Import(ConnectionTestDoubles.class)
@ExtendWith(OutputCaptureExtension.class)
class ConnectionApiTest {

    private static final String SECRET = "SECRET-never-leak-9f3a7c";
    private static final AtomicInteger USERS = new AtomicInteger();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private StubBrokerVerifier verifier;
    @Autowired
    private RecordingIngestScheduler scheduler;
    @Autowired
    private RecordingPurger purger;
    @Autowired
    private CredentialReader credentialReader;
    @Autowired
    private BrokerCredentialVault vault;
    @Autowired
    private PortfolioRepository repository;
    @Autowired
    private PortfolioService portfolio;
    @Autowired
    private JdbcTemplate jdbc;

    private final List<String> bodies = new ArrayList<>();
    private Cookie session;

    @BeforeEach
    void login() throws Exception {
        verifier.reset();
        scheduler.reset();
        session = Sessions.devLogin(mvc, "연결" + USERS.incrementAndGet());
    }

    @AfterEach
    void 키는_응답과_로그_어디에도_없다(CapturedOutput output) {
        assertThat(bodies).isNotEmpty().noneMatch(b -> b.contains(SECRET));
        assertThat(output.getAll()).doesNotContain(SECRET);
    }

    private ResultActions perform(RequestBuilder request) throws Exception {
        ResultActions actions = mvc.perform(request);
        MvcResult result = actions.andReturn();
        bodies.add(result.getResponse().getContentAsString());
        result.getResponse().getHeaderNames().forEach(h -> bodies.add(h + ": " + result.getResponse().getHeaders(h)));
        return actions;
    }

    private MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder builder) {
        return builder.cookie(session).header("Origin", Sessions.WEB_ORIGIN);
    }

    private ResultActions connect(String clientId, String clientSecret) throws Exception {
        return perform(write(post("/api/connections/toss")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"" + clientId + "\",\"clientSecret\":\"" + clientSecret + "\"}"));
    }

    private long accountIdOfMe() throws Exception {
        String me = mvc.perform(get("/api/me").cookie(session)).andReturn().getResponse().getContentAsString();
        int userId = com.jayway.jsonpath.JsonPath.read(me, "$.user.id");
        return repository.activeAccountOf(userId).orElseThrow().id();
    }

    @Test
    void 종합매매_계좌가_하나면_바로_연결하고_역산을_예약한다() throws Exception {
        connect(" client-1 ", SECRET)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("BACKFILLING")))
                .andExpect(jsonPath("$.accounts").doesNotExist());

        long accountId = accountIdOfMe();
        assertThat(verifier.lastCredentials.clientId()).isEqualTo("client-1");
        assertThat(scheduler.calls).containsExactly(new Call("BACKFILL", accountId, null));
        assertThat(credentialReader.read(accountId).clientSecret()).isEqualTo(SECRET);
        assertThat(credentialReader.read(accountId).clientId()).isEqualTo("client-1");
        // DB에도 평문이 없다
        byte[] stored = jdbc.queryForObject("SELECT secret_ciphertext FROM broker_credential WHERE account_id = ?", byte[].class, accountId);
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(SECRET);

        perform(get("/api/me").cookie(session))
                .andExpect(jsonPath("$.connection.status", is("BACKFILLING")))
                .andExpect(jsonPath("$.connection.maskedAccountNo", is("1234-**-**56")));
        perform(get("/api/connections/toss").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("BACKFILLING")))
                .andExpect(jsonPath("$.maskedAccountNo", is("1234-**-**56")))
                .andExpect(jsonPath("$.connectedAt", notNullValue()))
                .andExpect(jsonPath("$.backfill.state", is("RUNNING")))
                .andExpect(jsonPath("$.backfill.progress", is(0.42)))
                .andExpect(jsonPath("$.lastSyncedAt", nullValue()))
                .andExpect(jsonPath("$.lastError", nullValue()))
                .andExpect(jsonPath("$.recentGaps", hasSize(0)));

        scheduler.status = new IngestStatus("DONE", 1.0, ConnectionTestDoubles.SYNCED_AT, null, List.of(LocalDate.of(2026, 10, 12)));
        perform(get("/api/connections/toss").cookie(session))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.lastSyncedAt", is("2026-10-16T22:05:00Z")))
                .andExpect(jsonPath("$.recentGaps", contains("2026-10-12")));
        scheduler.status = new IngestStatus("DONE", 1.0, null, "다른 곳에서 같은 키를 사용 중", List.of());
        perform(get("/api/me").cookie(session)).andExpect(jsonPath("$.connection.status", is("ERROR")));

        // 이미 연결돼 있으면 검증하지 않고 409
        verifier.lastCredentials = null;
        connect("client-2", SECRET).andExpect(status().isConflict()).andExpect(jsonPath("$.code", is("ALREADY_CONNECTED")));
        assertThat(verifier.lastCredentials).isNull();
    }

    @Test
    void 종합매매_계좌가_여럿이면_고르게_하고_고른_계좌로_연결한다() throws Exception {
        verifier.accounts = List.of(
                new BrokerAccount("11", "1111-**-**11", "BROKERAGE"),
                new BrokerAccount("22", "2222-**-**22", "BROKERAGE"),
                new BrokerAccount("33", "3333-**-**33", "PENSION"));
        connect("client-m", SECRET)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CHOOSE_ACCOUNT")))
                .andExpect(jsonPath("$.accounts", hasSize(2)))
                .andExpect(jsonPath("$.accounts[1].accountSeq", is("22")))
                .andExpect(jsonPath("$.accounts[1].maskedAccountNo", is("2222-**-**22")))
                .andExpect(jsonPath("$.accounts[1].type", is("BROKERAGE")));
        // 고르기 전에는 아무것도 저장하지 않는다
        perform(get("/api/me").cookie(session)).andExpect(jsonPath("$.connection.status", is("NONE")));
        assertThat(scheduler.calls).isEmpty();

        perform(write(post("/api/connections/toss/account")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"client-m\",\"clientSecret\":\"" + SECRET + "\",\"accountSeq\":\"99\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));
        perform(write(post("/api/connections/toss/account")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"client-m\",\"clientSecret\":\"" + SECRET + "\",\"accountSeq\":\"22\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("BACKFILLING")));
        perform(get("/api/me").cookie(session)).andExpect(jsonPath("$.connection.maskedAccountNo", is("2222-**-**22")));
        assertThat(credentialReader.read(accountIdOfMe()).clientSecret()).isEqualTo(SECRET);
    }

    @Test
    void 검증_실패_사유마다_오류_코드가_정해져_있고_키는_저장하지_않는다() throws Exception {
        Map<Reason, Object[]> expected = Map.of(
                Reason.INVALID_CREDENTIALS, new Object[] {400, "TOSS_INVALID_CREDENTIALS"},
                Reason.IP_NOT_ALLOWED, new Object[] {400, "TOSS_IP_NOT_ALLOWED"},
                Reason.NO_BROKERAGE_ACCOUNT, new Object[] {400, "TOSS_NO_BROKERAGE_ACCOUNT"},
                Reason.RATE_LIMITED, new Object[] {429, "TOSS_RATE_LIMITED"},
                Reason.TOKEN_REVOKED, new Object[] {502, "TOSS_UNAVAILABLE"},
                Reason.UNAVAILABLE, new Object[] {502, "TOSS_UNAVAILABLE"});
        assertThat(expected).hasSize(Reason.values().length);
        for (var e : expected.entrySet()) {
            verifier.failure = e.getKey();
            ResultActions r = connect("client-f", SECRET)
                    .andExpect(status().is((int) e.getValue()[0]))
                    .andExpect(jsonPath("$.code", is(e.getValue()[1])))
                    .andExpect(jsonPath("$.message", notNullValue()));
            if (e.getKey() == Reason.IP_NOT_ALLOWED) {
                r.andExpect(jsonPath("$.egressIps", contains("127.0.0.1")));
            } else {
                r.andExpect(jsonPath("$.egressIps").doesNotExist());
            }
        }
        verifier.failure = null;
        verifier.accounts = List.of(new BrokerAccount("5", "5555", "PENSION"));
        connect("client-f", SECRET).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", is("TOSS_NO_BROKERAGE_ACCOUNT")));

        perform(get("/api/me").cookie(session)).andExpect(jsonPath("$.connection.status", is("NONE")));
        assertThat(scheduler.calls).isEmpty();
    }

    @Test
    void 입력값을_검사하고_오류에_값을_되풀이하지_않는다() throws Exception {
        connect("  ", SECRET).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));
        connect("client", "").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));
        connect("client", SECRET + "x".repeat(500)).andExpect(status().isBadRequest());
        connect("c".repeat(201), SECRET).andExpect(status().isBadRequest());
        perform(write(post("/api/connections/toss")).contentType(MediaType.APPLICATION_JSON).content("{\"clientSecret\":\"" + SECRET))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));
        perform(write(delete("/api/connections/toss")).param("deleteData", "yes")).andExpect(status().isBadRequest());
        assertThat(verifier.lastCredentials).isNull();
    }

    @Test
    void 다시_수집은_계좌당_1시간에_한_번이다() throws Exception {
        perform(write(post("/api/connections/toss/sync"))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_CONNECTED")));
        connect("client-s", SECRET).andExpect(status().isCreated());
        long accountId = accountIdOfMe();

        perform(write(post("/api/connections/toss/sync"))).andExpect(status().isAccepted());
        perform(write(post("/api/connections/toss/sync")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code", is("SYNC_TOO_SOON")));
        assertThat(scheduler.calls).filteredOn(c -> c.kind().equals("DAILY")).singleElement()
                .satisfies(c -> {
                    assertThat(c.accountId()).isEqualTo(accountId);
                    assertThat(c.asOf()).isBefore(LocalDate.now().plusDays(1));
                    assertThat(c.asOf().getDayOfWeek().getValue()).isLessThanOrEqualTo(5);
                });
    }

    @Test
    void 연결_해제는_키를_즉시_파기하고_기록은_남긴다_같은_계좌를_다시_연결하면_이어_쓴다() throws Exception {
        connect("client-d", SECRET).andExpect(status().isCreated());
        long accountId = accountIdOfMe();
        copyDemoData(accountId);

        perform(write(delete("/api/connections/toss"))).andExpect(status().isNoContent());
        assertThat(vault.exists(accountId)).isFalse();
        assertThatThrownBy(() -> credentialReader.read(accountId)).isInstanceOf(CredentialReader.CredentialNotFoundException.class);
        assertThat(repository.findAccount(accountId).orElseThrow().status()).isEqualTo(PortfolioRepository.AccountStatus.DISCONNECTED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trade WHERE account_id = ?", Long.class, accountId)).isPositive();
        perform(get("/api/me").cookie(session)).andExpect(jsonPath("$.connection.status", is("NONE")));
        perform(get("/api/portfolio/summary").cookie(session)).andExpect(status().isNotFound());

        connect("client-d", SECRET).andExpect(status().isCreated());
        assertThat(accountIdOfMe()).isEqualTo(accountId);
        assertThat(vault.exists(accountId)).isTrue();
    }

    @Test
    void 기록도_삭제하면_원시_파생_기록과_계좌를_지운다() throws Exception {
        connect("client-x", SECRET).andExpect(status().isCreated());
        long accountId = accountIdOfMe();
        copyDemoData(accountId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM account_day WHERE account_id = ?", Long.class, accountId)).isPositive();

        perform(write(delete("/api/connections/toss")).param("deleteData", "true")).andExpect(status().isNoContent());
        assertThat(vault.exists(accountId)).isFalse();
        assertThat(purger.purged).contains(accountId);
        for (String table : new String[] {"trade", "cash_flow", "dividend", "collection_gap", "account_day"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE account_id = ?", Long.class, accountId))
                    .as(table).isZero();
        }
        assertThat(repository.findAccount(accountId)).isEmpty();
        perform(write(delete("/api/connections/toss")).param("deleteData", "true")).andExpect(status().isNotFound());
        // 데모 계좌는 그대로
        perform(get("/api/portfolio/summary")).andExpect(jsonPath("$.account.sample", is(true)));
    }

    @Test
    void 포트폴리오_API는_로그인_사용자의_계좌를_쓴다() throws Exception {
        connect("client-p", SECRET).andExpect(status().isCreated());
        long accountId = accountIdOfMe();
        copyDemoData(accountId);

        perform(get("/api/portfolio/summary").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.name", is("토스증권 계좌")))
                .andExpect(jsonPath("$.account.sample", is(false)));
        perform(get("/api/portfolio/summary"))
                .andExpect(jsonPath("$.account.name", is("데모 계좌")))
                .andExpect(jsonPath("$.account.sample", is(true)));
        // 다른 사용자는 이 계좌를 보지 못한다
        Cookie other = Sessions.devLogin(mvc, "남의계좌" + USERS.incrementAndGet());
        perform(get("/api/portfolio/summary").cookie(other)).andExpect(status().isNotFound());
    }

    /** 데모 계좌의 체결·입출금을 이 계좌에 복사하고 account_day를 만든다. */
    private void copyDemoData(long accountId) {
        long demo = repository.demoAccount().orElseThrow().id();
        repository.insertCashFlows(accountId, repository.cashFlows(demo), "copy-f-");
        repository.insertTrades(accountId, repository.trades(demo), "copy-t-");
        // 연결한 계좌는 실제 USDKRW를 쓴다. 테스트에선 데모용 합성 환율을 실제 pair로 복사해 둔다.
        jdbc.update("""
                INSERT INTO fx_rate (pair, date, rate)
                SELECT 'USDKRW', s.date, s.rate FROM fx_rate s
                WHERE s.pair = 'USDKRW_SAMPLE'
                  AND NOT EXISTS (SELECT 1 FROM fx_rate r WHERE r.pair = 'USDKRW' AND r.date = s.date)
                """);
        portfolio.refresh(accountId);
    }
}
