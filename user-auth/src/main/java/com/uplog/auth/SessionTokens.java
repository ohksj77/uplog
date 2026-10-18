package com.uplog.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * 서명한 JWT(HS256). 세션 토큰(용도 {@code session}, sub = 사용자 id)과 OAuth2 왕복용 임시 토큰을 같은 키로 만들되,
 * {@code use} 클레임으로 용도를 나눠 서로 바꿔 쓸 수 없게 한다.
 */
public final class SessionTokens {

    static final String ISSUER = "uplog";
    static final String USE = "use";
    static final String USE_SESSION = "session";

    private static final Logger log = LoggerFactory.getLogger(SessionTokens.class);
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Duration sessionTtl;
    private final Clock clock;

    SessionTokens(SecretKey key, Duration sessionTtl, Clock clock) {
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestamps.setClock(clock);
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamps,
                new JwtIssuerValidator(ISSUER),
                jwt -> jwt.getExpiresAt() != null
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "exp 없음", null))));
        this.decoder = nimbus;
        this.sessionTtl = sessionTtl;
        this.clock = clock;
    }

    /** 설정의 키로 만든다. 키가 비어 있으면 실행마다 무작위 키를 쓴다. */
    public static SessionTokens create(AuthProperties properties, Clock clock) {
        String secret = properties.sessionSecret();
        byte[] bytes;
        if (secret == null || secret.isBlank()) {
            log.warn("UPLOG_SESSION_SECRET이 없어 무작위 세션 키를 씁니다. 재시작하면 모든 사용자가 로그아웃됩니다.");
            bytes = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(bytes);
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException("UPLOG_SESSION_SECRET은 32바이트 이상이어야 합니다.");
            }
        }
        return new SessionTokens(new SecretKeySpec(bytes, "HmacSHA256"), properties.sessionTtl(), clock);
    }

    public Duration sessionTtl() {
        return sessionTtl;
    }

    public String issueSession(long userId) {
        return issue(USE_SESSION, String.valueOf(userId), sessionTtl, Map.of());
    }

    /** 세션 토큰이면 사용자 id. 서명·만료·용도가 하나라도 맞지 않으면 빈 값. */
    public Optional<Long> verifySession(String token) {
        return verify(USE_SESSION, token).flatMap(jwt -> {
            try {
                return Optional.of(Long.parseLong(jwt.getSubject()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    String issue(String use, String subject, Duration ttl, Map<String, Object> claims) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder set = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(USE, use);
        claims.forEach(set::claim);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), set.build()))
                .getTokenValue();
    }

    Optional<Jwt> verify(String use, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Jwt jwt = decoder.decode(token);
            return use.equals(jwt.getClaimAsString(USE)) ? Optional.of(jwt) : Optional.empty();
        } catch (JwtException e) {
            return Optional.empty();
        }
    }
}
