package com.uplog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 테스트용 개발 로그인. 웹 출처(Origin)를 붙여 보낸다. */
final class Sessions {

    static final String WEB_ORIGIN = "http://localhost:3000";

    private Sessions() {}

    static Cookie devLogin(MockMvc mvc, String nickname) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/dev-login")
                        .header("Origin", WEB_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"" + nickname + "\"}"))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return sessionCookie(result);
    }

    static Cookie sessionCookie(MvcResult result) {
        String header = result.getResponse().getHeader("Set-Cookie");
        assertThat(header).startsWith("uplog_session=");
        String value = header.substring("uplog_session=".length(), header.indexOf(';'));
        return new Cookie("uplog_session", value);
    }
}
