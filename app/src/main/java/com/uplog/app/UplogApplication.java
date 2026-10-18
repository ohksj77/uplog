package com.uplog.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 맥 앱의 로컬 엔진(docs/design/desktop-app.md "실행 계약"). 기본 프로필 {@code desktop}: 127.0.0.1에만 바인딩하고,
 * H2 파일 DB·로그를 {@code uplog.desktop.data-dir}에 두며, {@code uplog.desktop.web-dir}의 화면을 서빙한다.
 * 테스트·개발용 가짜 토스 서버는 {@code fake-toss}를 함께 켠다.
 */
@SpringBootApplication(scanBasePackages = "com.uplog")
public class UplogApplication {

    public static void main(String[] args) {
        SpringApplication.run(UplogApplication.class, args);
    }
}
