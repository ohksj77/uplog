package com.uplog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** local 프로필: 비로그인·로그인 접근, 401 형식, Origin 검사, 개발용 로그인과 세션 쿠키. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "api"})
class AuthSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void 비로그인은_데모_계좌를_보고_me는_user가_null이다() throws Exception {
        mvc.perform(get("/api/portfolio/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.sample", is(true)))
                .andExpect(jsonPath("$.account.name", is("데모 계좌")));
        mvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user", nullValue()))
                .andExpect(jsonPath("$.connection.status", is("NONE")))
                .andExpect(jsonPath("$.connection.maskedAccountNo", nullValue()));
        mvc.perform(get("/api/etfs")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void 비로그인으로_연결_API를_부르면_리다이렉트_없이_401_JSON이다() throws Exception {
        for (String url : new String[] {"/api/connections/toss", "/api/connections/toss/setup"}) {
            mvc.perform(get(url))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")))
                    .andExpect(jsonPath("$.message", is("로그인이 필요합니다.")));
        }
        mvc.perform(post("/api/connections/toss").header("Origin", Sessions.WEB_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("UNAUTHENTICATED")));
        mvc.perform(delete("/api/connections/toss").header("Origin", Sessions.WEB_ORIGIN))
                .andExpect(status().isUnauthorized());
        // 새로 생길 /api 엔드포인트도 기본으로 잠긴다
        mvc.perform(get("/api/anything-new")).andExpect(status().isUnauthorized());
    }

    @Test
    void 개발용_로그인은_HttpOnly_SameSite_Lax_14일_세션_쿠키를_준다() throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/dev-login").header("Origin", Sessions.WEB_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"보안테스트\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nickname", is("보안테스트")))
                .andExpect(jsonPath("$.provider", is("DEV")))
                .andReturn();
        String header = result.getResponse().getHeader("Set-Cookie");
        assertThat(header).contains("HttpOnly", "SameSite=Lax", "Path=/", "Max-Age=1209600").doesNotContain("Secure");

        Cookie session = Sessions.sessionCookie(result);
        mvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.nickname", is("보안테스트")))
                .andExpect(jsonPath("$.user.provider", is("DEV")))
                .andExpect(jsonPath("$.connection.status", is("NONE")));
        // 같은 닉네임이면 같은 사용자
        int id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/auth/dev-login").header("Origin", Sessions.WEB_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"보안테스트\"}"))
                .andExpect(jsonPath("$.id", is(id)));
    }

    @Test
    void 개발용_로그인은_닉네임을_검사한다() throws Exception {
        for (String body : new String[] {"{\"nickname\":\"  \"}", "{}", "{\"nickname\":\"" + "가".repeat(21) + "\"}"}) {
            mvc.perform(post("/api/auth/dev-login").header("Origin", Sessions.WEB_ORIGIN)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));
        }
    }

    @Test
    void 계좌가_없는_로그인_사용자는_데모가_아니라_404_ACCOUNT_NOT_CONNECTED다() throws Exception {
        Cookie session = Sessions.devLogin(mvc, "계좌없음");
        for (String url : new String[] {"/api/portfolio/summary", "/api/portfolio/log", "/api/portfolio/insights/fx"}) {
            mvc.perform(get(url).cookie(session))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_CONNECTED")));
        }
        mvc.perform(get("/api/connections/toss").cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_CONNECTED")));
    }

    @Test
    void 상태를_바꾸는_요청은_다른_출처나_출처_없는_쿠키_요청을_403으로_막는다() throws Exception {
        Cookie session = Sessions.devLogin(mvc, "출처검사");
        mvc.perform(post("/api/auth/logout").cookie(session).header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ORIGIN_NOT_ALLOWED")));
        mvc.perform(delete("/api/connections/toss").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ORIGIN_NOT_ALLOWED")));
        mvc.perform(post("/api/auth/logout").cookie(session).header("Referer", "https://evil.example/page"))
                .andExpect(status().isForbidden());
        // 허용한 웹 출처와 자기 출처는 통과
        mvc.perform(post("/api/auth/logout").cookie(session).header("Origin", Sessions.WEB_ORIGIN))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").cookie(session).header("Origin", "http://localhost"))
                .andExpect(status().isNoContent());
    }

    @Test
    void 로그아웃은_쿠키를_지우고_변조한_쿠키는_비로그인으로_본다() throws Exception {
        Cookie session = Sessions.devLogin(mvc, "로그아웃");
        MvcResult result = mvc.perform(post("/api/auth/logout").cookie(session).header("Origin", Sessions.WEB_ORIGIN))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(result.getResponse().getHeader("Set-Cookie")).startsWith("uplog_session=;").contains("Max-Age=0");

        Cookie tampered = new Cookie("uplog_session", session.getValue().substring(0, session.getValue().length() - 3) + "abc");
        mvc.perform(get("/api/me").cookie(tampered)).andExpect(jsonPath("$.user", nullValue()));
        mvc.perform(get("/api/portfolio/summary").cookie(tampered)).andExpect(jsonPath("$.account.sample", is(true)));
        mvc.perform(get("/api/connections/toss").cookie(tampered)).andExpect(status().isUnauthorized());
    }

    @Test
    void 로그인_수단은_local에서_개발용만_켜진다() throws Exception {
        mvc.perform(get("/api/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kakao", is(false)))
                .andExpect(jsonPath("$.dev", is(true)));
        mvc.perform(get("/oauth2/authorization/kakao")).andExpect(status().isNotFound());
    }

    @Test
    void local의_가짜_토스_경로는_열려_있고_Origin_검사를_하지_않는다() throws Exception {
        int status = mvc.perform(post("/__fake-toss/oauth2/token")).andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }
}
