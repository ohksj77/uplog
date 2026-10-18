package com.uplog.app;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** local이 아닌 api 프로필: 개발용 로그인과 가짜 토스 경로가 없고, 데모 계좌도 없다. DB만 빈 H2로 바꾼다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:uplog-auth-api;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@ActiveProfiles("api")
class AuthWithoutLocalProfileTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void 개발용_로그인은_local_프로필에만_있다() throws Exception {
        mvc.perform(post("/api/auth/dev-login").header("Origin", Sessions.WEB_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"침입자\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/auth/providers"))
                .andExpect(jsonPath("$.kakao", is(false)))
                .andExpect(jsonPath("$.dev", is(false)));
    }

    @Test
    void 가짜_토스_경로는_막힌다() throws Exception {
        mvc.perform(get("/__fake-toss/api/v1/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    void 데모_계좌가_없으면_비로그인도_404_ACCOUNT_NOT_CONNECTED다() throws Exception {
        mvc.perform(get("/api/portfolio/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_CONNECTED")));
    }
}
