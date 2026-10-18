package com.uplog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 로컬 엔진 보안(dev-open 끔, 실행 토큰 있음)과 정적 화면 서빙. 요청은 엔진이 듣는 포트로 들어온 것처럼 꾸민다.
 * docs/design/desktop-app.md "실행 계약".
 */
@SpringBootTest(properties = {
    "uplog.desktop.dev-open=false",
    "uplog.desktop.launch-token=" + DesktopSecurityTest.TOKEN
})
@AutoConfigureMockMvc
@ActiveProfiles("desktop")
class DesktopSecurityTest {

    static final String TOKEN = "Zx8uQ1w7Yb2Nc5Kd0Lf3Mg6Ph9Rj4Sk7Tm1Vn2Wp5X";
    static final int PORT = 51734;
    static final String SELF = "127.0.0.1:" + PORT;
    static final Cookie LAUNCH = new Cookie("uplog_desktop", TOKEN);

    private static Path web;

    @Autowired
    private MockMvc mvc;

    @DynamicPropertySource
    static void webDir(DynamicPropertyRegistry registry) throws IOException {
        web = Files.createTempDirectory("uplog-web");
        write("index.html", "<h1>home</h1>");
        write("connect.html", "<h1>connect</h1>");
        write("connect/__next.connect.txt", "rsc-connect");
        write("__next.root.txt", "rsc-root");
        write("settings/connection.html", "<h1>settings</h1>");
        write("insights/index.html", "<h1>insights</h1>");
        write("_next/static/chunks/app-3f2a.js", "console.log(1)");
        write("404.html", "<h1>없는 화면</h1>");
        Files.writeString(web.getParent().resolve("uplog-secret.txt"), "secret");
        registry.add("uplog.desktop.web-dir", () -> web.toString());
    }

    private static void write(String path, String body) throws IOException {
        Path file = web.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, body);
    }

    /** 엔진 포트로 들어온 요청. host는 브라우저가 보낸 Host 헤더. */
    private static MockHttpServletRequestBuilder at(MockHttpServletRequestBuilder builder, String host) {
        return builder.header("Host", host).with(r -> {
            r.setLocalPort(PORT);
            return r;
        });
    }

    private static MockHttpServletRequestBuilder at(MockHttpServletRequestBuilder builder) {
        return at(builder, SELF);
    }

    @Test
    void 준비_확인은_열려_있다() throws Exception {
        mvc.perform(at(get("/__health"))).andExpect(status().isOk()).andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    void 실행_쿠키가_없거나_틀리면_api는_403_DESKTOP_LOCKED다() throws Exception {
        for (String url : new String[] {"/api/me", "/api/portfolio/summary", "/api/connections/toss/setup",
            "/api/desktop/public-ip", "/api/anything-new"}) {
            mvc.perform(at(get(url)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code", is("DESKTOP_LOCKED")))
                    .andExpect(jsonPath("$.message", is("UpLog 앱에서 열어 주세요. 앱을 다시 열면 이어서 쓸 수 있어요.")));
        }
        mvc.perform(at(get("/api/me")).cookie(new Cookie("uplog_desktop", TOKEN.substring(1))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("DESKTOP_LOCKED")));
        mvc.perform(at(post("/api/connections/toss")).header("Origin", "http://" + SELF)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("DESKTOP_LOCKED")));
    }

    @Test
    void 실행_토큰을_내면_쿠키를_주고_루트로_보낸다_틀리면_403이다() throws Exception {
        for (String wrong : new String[] {TOKEN + "x", "", "short"}) {
            MvcResult r = mvc.perform(at(get("/__launch")).param("token", wrong))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code", is("DESKTOP_LOCKED")))
                    .andReturn();
            assertThat(r.getResponse().getHeader("Set-Cookie")).isNull();
        }
        mvc.perform(at(get("/__launch"))).andExpect(status().isForbidden());

        MvcResult ok = mvc.perform(at(get("/__launch")).param("token", TOKEN))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn();
        String cookie = ok.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).startsWith("uplog_desktop=" + TOKEN + ";").contains("HttpOnly", "SameSite=Strict", "Path=/")
                .doesNotContain("Secure");

        mvc.perform(at(get("/api/me")).cookie(LAUNCH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.provider", is("LOCAL")))
                .andExpect(jsonPath("$.connection.status", is("NONE")));
        // 토스를 연결하기 전에는 데모 계좌
        mvc.perform(at(get("/api/portfolio/summary")).cookie(LAUNCH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.sample", is(true)));
    }

    @Test
    void Host가_루프백_이름과_엔진_포트가_아니면_모두_403이다() throws Exception {
        for (String host : new String[] {"evil.com", "evil.com:" + PORT, "127.0.0.1:8080", "192.168.0.2:" + PORT, "uplog.local:" + PORT}) {
            mvc.perform(at(get("/__health"), host))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code", is("HOST_NOT_ALLOWED")));
            mvc.perform(at(get("/api/me"), host).cookie(LAUNCH)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code", is("HOST_NOT_ALLOWED")));
            mvc.perform(at(get("/"), host)).andExpect(status().isForbidden());
            MvcResult launch = mvc.perform(at(get("/__launch"), host).param("token", TOKEN))
                    .andExpect(status().isForbidden()).andReturn();
            assertThat(launch.getResponse().getHeader("Set-Cookie")).isNull();
        }
        mvc.perform(at(get("/api/me"), "localhost:" + PORT).cookie(LAUNCH)).andExpect(status().isOk());
    }

    @Test
    void 상태를_바꾸는_요청은_같은_출처만_받는다() throws Exception {
        for (String origin : new String[] {"https://evil.example", "http://localhost:3000", "http://127.0.0.1:8080", "null"}) {
            mvc.perform(at(post("/api/connections/toss/sync")).cookie(LAUNCH).header("Origin", origin))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code", is("ORIGIN_NOT_ALLOWED")));
        }
        // 쿠키를 들고 Origin·Referer 없이 오는 요청은 믿지 않는다
        mvc.perform(at(post("/api/connections/toss/sync")).cookie(LAUNCH))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ORIGIN_NOT_ALLOWED")));
        mvc.perform(at(post("/api/connections/toss/sync")).cookie(LAUNCH).header("Referer", "https://evil.example/x"))
                .andExpect(status().isForbidden());
        // 같은 출처는 통과(연결이 없어 404)
        mvc.perform(at(post("/api/connections/toss/sync")).cookie(LAUNCH).header("Origin", "http://" + SELF))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_CONNECTED")));
        mvc.perform(at(post("/api/connections/toss/sync"), "localhost:" + PORT).cookie(LAUNCH)
                        .header("Origin", "http://localhost:" + PORT))
                .andExpect(status().isNotFound());
    }

    @Test
    void 화면은_파일_html_index_순으로_찾고_없으면_404_html이다() throws Exception {
        mvc.perform(at(get("/")))
                .andExpect(status().isOk())
                .andExpect(content().string("<h1>home</h1>"))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        // connect/ 폴더(페이로드만)가 있어도 connect.html
        mvc.perform(at(get("/connect"))).andExpect(status().isOk()).andExpect(content().string("<h1>connect</h1>"));
        mvc.perform(at(get("/connect/"))).andExpect(status().isOk()).andExpect(content().string("<h1>connect</h1>"));
        mvc.perform(at(get("/connect.html"))).andExpect(content().string("<h1>connect</h1>"));
        mvc.perform(at(get("/settings/connection")).param("x", "1")).andExpect(content().string("<h1>settings</h1>"));
        mvc.perform(at(get("/insights"))).andExpect(content().string("<h1>insights</h1>"));
        // RSC 페이로드는 text/plain
        mvc.perform(at(get("/connect/__next.connect.txt")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
                .andExpect(content().string("rsc-connect"));
        mvc.perform(at(get("/__next.root.txt"))).andExpect(status().isOk()).andExpect(content().string("rsc-root"));
        // 해시가 붙은 정적 파일은 오래 캐시
        mvc.perform(at(get("/_next/static/chunks/app-3f2a.js")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=31536000, public, immutable"))
                .andExpect(content().contentTypeCompatibleWith("text/javascript"));
        mvc.perform(at(head("/connect"))).andExpect(status().isOk());

        mvc.perform(at(get("/nope")))
                .andExpect(status().isNotFound())
                .andExpect(content().string("<h1>없는 화면</h1>"));
        mvc.perform(at(get("/login"))).andExpect(status().isNotFound());
        // web-dir 밖은 보이지 않는다
        int traversal = mvc.perform(at(get("/../uplog-secret.txt"))).andReturn().getResponse().getStatus();
        assertThat(traversal).isIn(400, 404);
        int encoded = mvc.perform(at(get("/%2e%2e/uplog-secret.txt"))).andReturn().getResponse().getStatus();
        assertThat(encoded).isIn(400, 404);
        // 없는 API는 화면이 아니라 잠김(쿠키 없음)·404(쿠키 있음)
        mvc.perform(at(get("/api/nope"))).andExpect(status().isForbidden());
        mvc.perform(at(get("/api/nope")).cookie(LAUNCH)).andExpect(status().isNotFound());
    }
}
