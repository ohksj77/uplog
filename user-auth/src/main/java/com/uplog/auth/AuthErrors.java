package com.uplog.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;

/** 보안 필터에서 내는 오류 응답. 공통 오류 형식 {@code {code, message}}를 따르고, 리다이렉트하지 않는다. */
public final class AuthErrors {

    /** 오류 응답 본문 */
    public record ErrorResponse(String code, String message) {}

    static final String UNAUTHENTICATED_MESSAGE = "로그인이 필요합니다.";

    private AuthErrors() {}

    static void unauthenticated(HttpServletResponse response) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", UNAUTHENTICATED_MESSAGE);
    }

    static void forbidden(HttpServletResponse response) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "접근할 수 없습니다.");
    }

    static void originRejected(HttpServletResponse response) throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, "ORIGIN_NOT_ALLOWED", "허용되지 않은 출처에서 온 요청입니다.");
    }

    /** code·message는 이 클래스의 상수만 넣는다(사용자 입력이 아니라서 따로 이스케이프하지 않는다). */
    private static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
