package com.uplog.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 폰에서 시작한 연결을 PC에서 이어 하기(토스 WTS는 PC 웹이라서). 로그인한 기기가 1회용 코드를 만들고,
 * 다른 기기가 그 코드를 내면 같은 사용자의 세션을 받는다. 코드는 10분 뒤 만료되고 한 번만 쓸 수 있으며 해시만 저장한다.
 */
@Component
public class LoginHandoff {

    static final Duration TTL = Duration.ofMinutes(10);

    private static final SecureRandom RANDOM = new SecureRandom();

    public record Issued(String token, Instant expiresAt) {}

    private final JdbcClient jdbc;

    public LoginHandoff(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Issued issue(long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(TTL);
        jdbc.sql("INSERT INTO login_handoff (token_hash, user_id, expires_at) VALUES (:hash, :userId, :expiresAt)")
                .param("hash", hash(token))
                .param("userId", userId)
                .param("expiresAt", Timestamp.from(expiresAt))
                .update();
        return new Issued(token, expiresAt);
    }

    /** 유효한 코드면 쓴 것으로 표시하고 사용자 id를 준다. 만료됐거나 이미 썼거나 없는 코드면 empty. */
    public Optional<Long> redeem(String token) {
        if (token == null || token.isBlank() || token.length() > 100) {
            return Optional.empty();
        }
        String hash = hash(token.strip());
        Timestamp now = Timestamp.from(Instant.now());
        int updated = jdbc.sql("""
                        UPDATE login_handoff SET used_at = :now
                        WHERE token_hash = :hash AND used_at IS NULL AND expires_at > :now
                        """)
                .param("now", now)
                .param("hash", hash)
                .update();
        if (updated != 1) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT user_id FROM login_handoff WHERE token_hash = :hash")
                .param("hash", hash)
                .query(Long.class)
                .optional();
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
