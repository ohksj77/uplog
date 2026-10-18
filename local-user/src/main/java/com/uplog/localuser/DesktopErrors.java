package com.uplog.localuser;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 로컬 엔진 보안 필터의 오류 응답. 공통 오류 형식 {@code {code, message}}를 따르고, 리다이렉트하지 않는다. */
public final class DesktopErrors {

    /** 오류 응답 본문 */
    public record ErrorResponse(String code, String message) {}

    public static final String DESKTOP_LOCKED = "DESKTOP_LOCKED";
    static final String LOCKED_MESSAGE = "업로그 앱에서 열어 주세요. 앱을 다시 열면 이어서 쓸 수 있어요.";

    private DesktopErrors() {}

    static void locked(HttpServletResponse response) throws IOException {
        write(response, DESKTOP_LOCKED, LOCKED_MESSAGE);
    }

    static void hostRejected(HttpServletResponse response) throws IOException {
        write(response, "HOST_NOT_ALLOWED", "허용되지 않은 주소로 온 요청입니다.");
    }

    static void originRejected(HttpServletResponse response) throws IOException {
        write(response, "ORIGIN_NOT_ALLOWED", "허용되지 않은 출처에서 온 요청입니다.");
    }

    /** 모두 403. code·message는 이 클래스의 상수만 넣는다(사용자 입력이 아니라서 따로 이스케이프하지 않는다). */
    private static void write(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    /** 컨트롤러에서 {@link CurrentUser}가 필요한데 실행 쿠키가 없으면 403. */
    @RestControllerAdvice
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public static class LockedHandler {

        @ExceptionHandler(DesktopLockedException.class)
        public ResponseEntity<ErrorResponse> handle(DesktopLockedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(DESKTOP_LOCKED, e.getMessage()));
        }
    }
}
