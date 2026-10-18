package com.uplog.app;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 시드가 없는 기본(api) 프로필 구성: 계좌가 없으면 모든 계좌 API가 404 ACCOUNT_NOT_CONNECTED다. DB만 빈 H2로 바꾼다. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:uplog-empty;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password="
})
@AutoConfigureMockMvc
@ActiveProfiles("api")
class PortfolioWithoutAccountTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void 연결된_계좌가_없으면_404_ACCOUNT_NOT_CONNECTED다() throws Exception {
        for (String url : new String[] {"/api/portfolio/summary", "/api/portfolio/log", "/api/portfolio/insights/fx"}) {
            mvc.perform(get(url))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_CONNECTED")));
        }
        // 파라미터 오류는 계좌보다 먼저 본다
        mvc.perform(get("/api/portfolio/insights/sales").param("horizon", "3"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));
    }
}
