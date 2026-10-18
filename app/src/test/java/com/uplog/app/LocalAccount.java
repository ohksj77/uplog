package com.uplog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import org.springframework.test.web.servlet.MockMvc;

/**
 * 테스트 도움: 로컬 사용자는 한 명이라 테스트끼리 연결 상태를 나눠 쓴다. 테스트 앞뒤로 연결을 모두 해제하고 기록도 지워
 * 데모 계좌만 보이는 처음 상태로 되돌린다. dev-open이라 실행 쿠키 없이, 허용한 개발 출처(next dev)로 보낸다.
 */
final class LocalAccount {

    static final String DEV_ORIGIN = "http://localhost:3000";

    private LocalAccount() {}

    static void reset(MockMvc mvc) throws Exception {
        for (int i = 0; i < 10; i++) {
            int status = mvc.perform(delete("/api/connections/toss").param("deleteData", "true").header("Origin", DEV_ORIGIN))
                    .andReturn().getResponse().getStatus();
            if (status == 404) {
                return;
            }
            assertThat(status).isEqualTo(204);
        }
        throw new AssertionError("연결을 모두 해제하지 못했다");
    }
}
