package com.uplog.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 프로필 {@code api}(기본)는 REST 서버, {@code batch}는 수집·계산 워커로 뜬다.
 * {@code local}을 함께 켜면 H2 메모리 DB와 샘플 시세로 Docker 없이 뜬다.
 */
@SpringBootApplication(scanBasePackages = "com.uplog")
public class UplogApplication {

    public static void main(String[] args) {
        SpringApplication.run(UplogApplication.class, args);
    }
}
