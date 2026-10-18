package com.uplog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 연결을 쉽게: 틀린 값이 무엇인지 알려 주기, 연결한 계좌의 키만 바꾸기, 다른 기기에서 이어 하기. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:uplog-ease;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
    "uplog.ingest.worker-enabled=false",
    "uplog.toss.rate-limit-multiplier=1000",
    "uplog.toss.fake-server.rate-limit-multiplier=1000"
})
@AutoConfigureMockMvc
@ActiveProfiles({"local", "api", "fake-toss"})
class ConnectionEaseTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    private MockHttpServletResponse send(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Cookie session, String body)
            throws Exception {
        request.header("Origin", Sessions.WEB_ORIGIN).contentType(MediaType.APPLICATION_JSON).content(body);
        if (session != null) {
            request.cookie(session);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    private static JsonNode json(MockHttpServletResponse r) throws Exception {
        return JSON.readTree(r.getContentAsString());
    }

    @Test
    void 키_인증이_실패하면_어느_값이_틀렸는지_알려_준다() throws Exception {
        Cookie session = Sessions.devLogin(mvc, "틀린키");

        var secret = send(post("/api/connections/toss"), session, "{\"clientId\":\"good-id\",\"clientSecret\":\"invalid\"}");
        assertThat(secret.getStatus()).isEqualTo(400);
        assertThat(json(secret).get("code").asString()).isEqualTo("TOSS_INVALID_CREDENTIALS");
        assertThat(json(secret).get("field").asString()).isEqualTo("clientSecret");

        var id = send(post("/api/connections/toss"), session, "{\"clientId\":\"bad-id-1\",\"clientSecret\":\"s\"}");
        assertThat(json(id).get("field").asString()).isEqualTo("clientId");
        assertThat(json(id).get("message").asString()).contains("client_id");
    }

    @Test
    void 연결한_계좌의_키만_바꿀_수_있고_검증한_키만_저장한다() throws Exception {
        Cookie session = Sessions.devLogin(mvc, "키바꾸기");
        assertThat(send(post("/api/connections/toss"), session, "{\"clientId\":\"first\",\"clientSecret\":\"s1\"}").getStatus())
                .isEqualTo(201);

        assertThat(send(put("/api/connections/toss/credentials"), session, "{\"clientId\":\"second\",\"clientSecret\":\"s2\"}")
                .getStatus()).isEqualTo(204);

        var wrong = send(put("/api/connections/toss/credentials"), session, "{\"clientId\":\"third\",\"clientSecret\":\"invalid\"}");
        assertThat(wrong.getStatus()).isEqualTo(400);
        assertThat(json(wrong).get("field").asString()).isEqualTo("clientSecret");
    }

    @Test
    void 이어_하기_코드로_다른_기기가_같은_사용자가_되고_코드는_한_번만_쓴다() throws Exception {
        Cookie phone = Sessions.devLogin(mvc, "폰에서");
        var issued = send(post("/api/auth/handoff"), phone, "{}");
        assertThat(issued.getStatus()).isEqualTo(200);
        String token = json(issued).get("token").asString();
        assertThat(token).hasSizeGreaterThan(40);

        var redeemed = send(post("/api/auth/handoff/redeem"), null, "{\"token\":\"" + token + "\"}");
        assertThat(redeemed.getStatus()).isEqualTo(200);
        assertThat(json(redeemed).get("nickname").asString()).isEqualTo("폰에서");
        Cookie pc = redeemed.getCookie("uplog_session");
        assertThat(pc).isNotNull();
        var me = mvc.perform(get("/api/me").cookie(pc)).andReturn().getResponse();
        assertThat(json(me).get("user").get("nickname").asString()).isEqualTo("폰에서");

        var again = send(post("/api/auth/handoff/redeem"), null, "{\"token\":\"" + token + "\"}");
        assertThat(again.getStatus()).isEqualTo(400);
        assertThat(json(again).get("code").asString()).isEqualTo("HANDOFF_INVALID");

        assertThat(send(post("/api/auth/handoff"), null, "{}").getStatus()).isEqualTo(401);
    }
}
