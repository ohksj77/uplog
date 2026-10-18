package com.uplog.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/** 세션 쿠키 {@code uplog_session}: HttpOnly, SameSite=Lax, Path=/, Secure는 설정({@code uplog.auth.secure-cookie}). */
public final class SessionCookies {

    public static final String SESSION = "uplog_session";

    private final SessionTokens tokens;
    private final boolean secure;

    public SessionCookies(SessionTokens tokens, boolean secure) {
        this.tokens = tokens;
        this.secure = secure;
    }

    /** 이 사용자의 새 세션을 쿠키로 내려 준다. */
    public void login(HttpServletResponse response, long userId) {
        set(response, SESSION, tokens.issueSession(userId), tokens.sessionTtl());
    }

    public void logout(HttpServletResponse response) {
        set(response, SESSION, "", Duration.ZERO);
    }

    public Optional<Long> userId(HttpServletRequest request) {
        return read(request, SESSION).flatMap(tokens::verifySession);
    }

    boolean hasSessionCookie(HttpServletRequest request) {
        return read(request, SESSION).isPresent();
    }

    void set(HttpServletResponse response, String name, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    static Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        for (Cookie c : cookies) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isEmpty()) {
                return Optional.of(c.getValue());
            }
        }
        return Optional.empty();
    }
}
