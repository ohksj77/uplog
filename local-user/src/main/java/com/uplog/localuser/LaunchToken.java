package com.uplog.localuser;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.http.ResponseCookie;

/**
 * 실행 토큰과 그것을 담는 쿠키 {@code uplog_desktop}(HttpOnly, SameSite=Strict, Path=/, 브라우저 세션 동안).
 * http://127.0.0.1이라 Secure는 붙이지 않는다. 비교는 SHA-256 다이제스트를 상수 시간으로 맞춰 본다.
 */
public final class LaunchToken {

    public static final String COOKIE = "uplog_desktop";

    private final byte[] digest;

    /** @param token 비어 있으면 어떤 값도 맞지 않는다 */
    public LaunchToken(String token) {
        this.digest = token == null || token.isBlank() ? null : sha256(token);
    }

    public boolean configured() {
        return digest != null;
    }

    public boolean matches(String candidate) {
        if (digest == null || candidate == null || candidate.isEmpty() || candidate.length() > 512) {
            return false;
        }
        return MessageDigest.isEqual(digest, sha256(candidate));
    }

    /** 요청의 실행 쿠키가 토큰과 맞는지. */
    public boolean matches(HttpServletRequest request) {
        return matches(cookieValue(request));
    }

    static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isEmpty()) {
                return c.getValue();
            }
        }
        return null;
    }

    static boolean hasCookie(HttpServletRequest request) {
        return cookieValue(request) != null;
    }

    /** 맞는 토큰을 그대로 쿠키 값으로 준다(엔진이 재시작하면 새 토큰이라 예전 쿠키는 저절로 무효). */
    static ResponseCookie cookie(String token) {
        return ResponseCookie.from(COOKIE, token).httpOnly(true).secure(false).sameSite("Strict").path("/").build();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return "LaunchToken[***]";
    }
}
