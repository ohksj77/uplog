package com.uplog.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SessionTokensTest {

    private static final String SECRET = "test-session-secret-at-least-32-bytes!";
    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");

    private static SessionTokens tokens(String secret, Instant now) {
        AuthProperties p = new AuthProperties(secret, false, Duration.ofDays(14), List.of(), new AuthProperties.Kakao(null, null, ""));
        return SessionTokens.create(p, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void 발급한_세션_토큰에서_사용자_id를_읽는다() {
        SessionTokens t = tokens(SECRET, NOW);
        assertThat(t.verifySession(t.issueSession(42))).contains(42L);
    }

    @Test
    void 다른_키_변조_만료_다른_용도의_토큰은_받지_않는다() {
        SessionTokens t = tokens(SECRET, NOW);
        String token = t.issueSession(42);

        assertThat(tokens(SECRET + "x", NOW).verifySession(token)).isEmpty();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
        assertThat(t.verifySession(tampered)).isEmpty();
        assertThat(tokens(SECRET, NOW.plus(Duration.ofDays(15))).verifySession(token)).isEmpty();
        assertThat(t.verifySession(t.issue("oauth2-request", "42", Duration.ofMinutes(5), Map.of()))).isEmpty();
        assertThat(t.verifySession("")).isEmpty();
        assertThat(t.verifySession("not.a.jwt")).isEmpty();
    }

    @Test
    void 짧은_키는_거절하고_빈_키면_무작위_키를_쓴다() {
        assertThatThrownBy(() -> tokens("short", NOW)).isInstanceOf(IllegalStateException.class);
        SessionTokens a = tokens("", NOW);
        SessionTokens b = tokens("", NOW);
        assertThat(b.verifySession(a.issueSession(1))).isEmpty();
    }

    @Test
    void 설정의_toString은_비밀을_가린다() {
        AuthProperties p = new AuthProperties(SECRET, false, Duration.ofDays(14), List.of(),
                new AuthProperties.Kakao("kakao-id", "kakao-secret", ""));
        assertThat(p.toString()).doesNotContain(SECRET).doesNotContain("kakao-secret");
    }

    @Test
    void 카카오_닉네임은_properties나_kakao_account에서_읽는다() {
        assertThat(KakaoLogin.nickname(Map.of("properties", Map.of("nickname", "승진")))).isEqualTo("승진");
        assertThat(KakaoLogin.nickname(Map.of("kakao_account", Map.of("profile", Map.of("nickname", "업로그")))))
                .isEqualTo("업로그");
        assertThat(KakaoLogin.nickname(Map.of("id", 1))).isEqualTo("카카오 사용자");
    }
}
