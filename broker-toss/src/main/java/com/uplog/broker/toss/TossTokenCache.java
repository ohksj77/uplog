package com.uplog.broker.toss;

import com.uplog.portfolio.connect.BrokerCredentials;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * clientId별 액세스 토큰 캐시. 토스는 클라이언트당 토큰이 하나뿐이라 새로 받으면 이전 토큰이 무효가 된다.
 * 그래서 발급은 한 번에 하나만 하고(동기화), 만료 1분 전부터 새로 받는다. secret은 담지 않고 다이제스트만 비교한다.
 */
final class TossTokenCache {

    private static final Duration EARLY_REFRESH = Duration.ofMinutes(1);

    record Token(String value, Instant expiresAt) {

        @Override
        public String toString() {
            return "Token[expiresAt=" + expiresAt + "]";
        }
    }

    private record Entry(byte[] secretDigest, Token token) {}

    private final Map<String, Entry> entries = new HashMap<>();
    private final Clock clock;

    TossTokenCache(Clock clock) {
        this.clock = clock;
    }

    synchronized String get(BrokerCredentials credentials, Function<BrokerCredentials, Token> issuer) {
        byte[] digest = digest(credentials.clientSecret());
        Entry e = entries.get(credentials.clientId());
        if (e != null && Arrays.equals(e.secretDigest(), digest)
                && clock.instant().plus(EARLY_REFRESH).isBefore(e.token().expiresAt())) {
            return e.token().value();
        }
        entries.remove(credentials.clientId());
        Token token = issuer.apply(credentials);
        entries.put(credentials.clientId(), new Entry(digest, token));
        return token.value();
    }

    /** 401을 받은 토큰을 버린다. 그 사이 다른 스레드가 새로 받았으면 그대로 둔다. */
    synchronized void invalidate(String clientId, String staleToken) {
        Entry e = entries.get(clientId);
        if (e != null && e.token().value().equals(staleToken)) {
            entries.remove(clientId);
        }
    }

    private static byte[] digest(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
