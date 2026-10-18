package com.uplog.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OriginCheckFilterTest {

    private final OriginCheckFilter filter = new OriginCheckFilter(List.of("http://localhost:3000"), List.of("/__fake-toss/"));

    private int run(String method, String path, String origin, String referer, boolean cookie) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServerName("localhost");
        request.setServerPort(8080);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        if (referer != null) {
            request.addHeader("Referer", referer);
        }
        if (cookie) {
            request.setCookies(new Cookie(SessionCookies.SESSION, "token"));
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return chain.getRequest() == null ? response.getStatus() : 0;
    }

    @Test
    void 허용한_출처와_자기_출처는_통과한다() throws Exception {
        assertThat(run("POST", "/api/x", "http://localhost:3000", null, true)).isZero();
        assertThat(run("POST", "/api/x", "http://localhost:8080", null, true)).isZero();
        assertThat(run("DELETE", "/api/x", null, "http://localhost:3000/settings", true)).isZero();
    }

    @Test
    void 다른_출처는_403이다() throws Exception {
        assertThat(run("POST", "/api/x", "https://evil.example", null, true)).isEqualTo(403);
        assertThat(run("POST", "/api/x", "null", null, false)).isEqualTo(403);
        assertThat(run("DELETE", "/api/x", null, "https://evil.example/a", true)).isEqualTo(403);
    }

    @Test
    void 출처가_없으면_세션_쿠키가_없을_때만_통과한다() throws Exception {
        assertThat(run("POST", "/api/x", null, null, false)).isZero();
        assertThat(run("POST", "/api/x", null, null, true)).isEqualTo(403);
    }

    @Test
    void 읽기_요청과_제외_경로는_검사하지_않는다() throws Exception {
        assertThat(run("GET", "/api/x", "https://evil.example", null, true)).isZero();
        assertThat(run("POST", "/__fake-toss/oauth2/token", null, null, true)).isZero();
    }
}
