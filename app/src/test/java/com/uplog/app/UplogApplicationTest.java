package com.uplog.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 기본 프로필은 PostgreSQL에 붙으므로, 컨텍스트 확인은 Docker 없이 도는 local 프로필로 한다.
@SpringBootTest
@ActiveProfiles({"local", "api"})
class UplogApplicationTest {

    @Test
    void 컨텍스트가_뜬다() {}
}
