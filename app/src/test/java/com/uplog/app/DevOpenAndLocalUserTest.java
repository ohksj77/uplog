package com.uplog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.uplog.localuser.LocalUsers;
import com.uplog.portfolio.publicip.PublicIpLookup;
import com.uplog.portfolio.publicip.PublicIpUnavailableException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** dev-open(개발): 토큰·Host 검사 없음. 로컬 사용자 한 명, 연결 안내의 공인 IP 확인(가짜 ipify). */
@SpringBootTest(properties = "uplog.public-ip.cache-ttl=0s") // 기억은 PublicIpLookupTest에서 본다
@AutoConfigureMockMvc
@ActiveProfiles("desktop")
class DevOpenAndLocalUserTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private LocalUsers users;
    @Autowired
    private FakeIpify ipify;

    static final class FakeIpify implements PublicIpLookup {
        final AtomicInteger calls = new AtomicInteger();
        volatile String ip = "203.0.113.7";

        @Override
        public String lookup() {
            calls.incrementAndGet();
            if (ip == null) {
                throw new PublicIpUnavailableException("down");
            }
            return ip;
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Doubles {
        @Bean
        FakeIpify fakeIpify() {
            return new FakeIpify();
        }
    }

    @Test
    void dev_open이면_쿠키와_Host_검사_없이_api를_쓴다() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Host", "evil.com")).andExpect(status().isOk());
        mvc.perform(get("/__health")).andExpect(status().isOk());
        // 토큰 없이 띄웠으면 /__launch는 쿠키 없이 루트로 보낸다
        MvcResult launch = mvc.perform(get("/__launch")).andExpect(status().isFound())
                .andExpect(header().string("Location", "/")).andReturn();
        assertThat(launch.getResponse().getHeader("Set-Cookie")).isNull();
    }

    @Test
    void 로컬_사용자는_한_명이고_모든_요청이_그_사용자다() throws Exception {
        String first = mvc.perform(get("/api/me"))
                .andExpect(jsonPath("$.user.nickname", is("나")))
                .andExpect(jsonPath("$.user.provider", is("LOCAL")))
                .andExpect(jsonPath("$.connection.status", is("NONE")))
                .andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(first, "$.user.id");
        mvc.perform(get("/api/me")).andExpect(jsonPath("$.user.id", is(id)));
        assertThat(users.currentId()).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class)).isEqualTo(1L);
        // 로그인·이어 하기 API는 없다
        mvc.perform(get("/api/auth/providers")).andExpect(status().isNotFound());
        mvc.perform(get("/oauth2/authorization/kakao")).andExpect(status().isNotFound());
    }

    @Test
    void 연결_안내는_이_맥의_공인_IP를_확인하는_API를_가리킨다() throws Exception {
        mvc.perform(get("/api/connections/toss/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicIpCheck", is("/api/desktop/public-ip")))
                .andExpect(jsonPath("$.egressIps").doesNotExist())
                .andExpect(jsonPath("$.simulated", is(false)))
                .andExpect(jsonPath("$.keyPageUrl", is("https://www.tossinvest.com/")))
                .andExpect(jsonPath("$.notes", hasItem(containsString("이 맥의 공인 IP"))))
                .andExpect(jsonPath("$.notes", hasItem(containsString("다시 추가"))));
    }

    @Test
    void 공인_IP는_누를_때만_확인하고_실패하면_502다() throws Exception {
        assertThat(ipify.calls).hasValue(0); // 시작할 때 부르지 않는다
        mvc.perform(get("/api/desktop/public-ip")).andExpect(status().isOk()).andExpect(jsonPath("$.ip", is("203.0.113.7")));
        assertThat(ipify.calls).hasValue(1);
        ipify.ip = null;
        mvc.perform(get("/api/desktop/public-ip"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code", is("PUBLIC_IP_UNAVAILABLE")));
        ipify.ip = "203.0.113.7";
    }
}
