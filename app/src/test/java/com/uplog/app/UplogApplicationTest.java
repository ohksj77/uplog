package com.uplog.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("desktop")
class UplogApplicationTest {

    @Test
    void 컨텍스트가_뜬다() {}
}
