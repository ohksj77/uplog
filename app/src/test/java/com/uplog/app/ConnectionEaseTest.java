package com.uplog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

/** 연결을 쉽게: 틀린 값이 무엇인지 알려 주기, 연결한 계좌의 키만 바꾸기. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "uplog.test.context=ConnectionEaseTest", // 클래스마다 따로 컨텍스트(새 메모리 DB)
    "uplog.ingest.worker-enabled=false",
    "uplog.toss.rate-limit-multiplier=1000",
    "uplog.toss.fake-server.rate-limit-multiplier=1000"
})
@AutoConfigureMockMvc
@ActiveProfiles({"desktop", "fake-toss"})
class ConnectionEaseTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    @AfterEach
    void reset() throws Exception {
        LocalAccount.reset(mvc);
    }

    private MockHttpServletResponse send(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header("Origin", LocalAccount.DEV_ORIGIN).contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(request).andReturn().getResponse();
    }

    private static JsonNode json(MockHttpServletResponse r) throws Exception {
        return JSON.readTree(r.getContentAsString());
    }

    @Test
    void 키_인증이_실패하면_어느_값이_틀렸는지_알려_준다() throws Exception {
        var secret = send(post("/api/connections/toss"), "{\"clientId\":\"good-id\",\"clientSecret\":\"invalid\"}");
        assertThat(secret.getStatus()).isEqualTo(400);
        assertThat(json(secret).get("code").asString()).isEqualTo("TOSS_INVALID_CREDENTIALS");
        assertThat(json(secret).get("field").asString()).isEqualTo("clientSecret");

        var id = send(post("/api/connections/toss"), "{\"clientId\":\"bad-id-1\",\"clientSecret\":\"s\"}");
        assertThat(json(id).get("field").asString()).isEqualTo("clientId");
        assertThat(json(id).get("message").asString()).contains("client_id");
    }

    @Test
    void 연결한_계좌의_키만_바꿀_수_있고_검증한_키만_저장한다() throws Exception {
        assertThat(send(post("/api/connections/toss"), "{\"clientId\":\"first\",\"clientSecret\":\"s1\"}").getStatus())
                .isEqualTo(201);

        assertThat(send(put("/api/connections/toss/credentials"), "{\"clientId\":\"second\",\"clientSecret\":\"s2\"}")
                .getStatus()).isEqualTo(204);

        var wrong = send(put("/api/connections/toss/credentials"), "{\"clientId\":\"third\",\"clientSecret\":\"invalid\"}");
        assertThat(wrong.getStatus()).isEqualTo(400);
        assertThat(json(wrong).get("field").asString()).isEqualTo("clientSecret");
    }
}
