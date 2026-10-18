package com.uplog.broker.toss;

import com.uplog.portfolio.connect.BrokerCredentials;
import com.uplog.portfolio.connect.BrokerException;
import com.uplog.portfolio.connect.BrokerException.Reason;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 토스증권 Open API 저수준 클라이언트(조회 전용).
 *
 * <ul>
 *   <li>토큰: clientId별 캐시. API가 401(token-revoked·expired-token·invalid-token)이면 한 번만 다시 받아 재시도하고,
 *       또 401이면 {@link Reason#TOKEN_REVOKED}.
 *   <li>레이트리밋: 그룹별 토큰 버킷. 429면 {@code Retry-After}만큼 쉬고 maxRetries번까지 다시 보낸 뒤 {@link Reason#RATE_LIMITED}.
 *   <li>5xx·연결 실패: 지수 백오프로 maxRetries번 다시 보낸 뒤 {@link Reason#UNAVAILABLE}.
 *   <li>허용 목록({@link TossOutboundGuard}) 밖의 요청은 보내기 전에 막는다.
 *   <li>키·토큰은 로그·예외 메시지에 넣지 않는다.
 * </ul>
 */
public class TossClient {

    private static final Logger log = LoggerFactory.getLogger(TossClient.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> RETRYABLE_TOKEN_ERRORS = Set.of("token-revoked", "expired-token", "invalid-token");
    private static final Duration BACKOFF_BASE = Duration.ofMillis(500);

    /** 응답에서 쓰는 것만. */
    record Raw(int status, String body, Integer retryAfter, Integer remaining, Integer reset) {

        boolean ok() {
            return status >= 200 && status < 300;
        }
    }

    private final RestClient http;
    private final Supplier<String> baseUrl;
    private final TossProperties properties;
    private final TossRateLimiter limiter;
    private final TossTokenCache tokens;
    private final Clock clock;
    private final Pacer pacer;

    public TossClient(RestClient.Builder builder, Supplier<String> baseUrl, TossProperties properties, Clock clock, Pacer pacer) {
        this.baseUrl = baseUrl;
        this.properties = properties;
        this.clock = clock;
        this.pacer = pacer;
        this.limiter = new TossRateLimiter(pacer, clock, properties.rateLimitMultiplier(), properties.lowRemaining());
        this.tokens = new TossTokenCache(clock);
        this.http = builder.requestInterceptor(new TossOutboundGuard(baseUrl)).build();
    }

    TossRateLimiter limiter() {
        return limiter;
    }

    /**
     * 계좌·시세 조회. 응답 envelope의 {@code result}를 돌려준다.
     *
     * @param accountSeq {@code X-Tossinvest-Account}, 계좌와 무관한 API면 null
     * @param query 값이 null인 항목은 빼고 보낸다
     * @param notFoundAsNull 404를 null로 돌려줄지(상장폐지 종목의 일봉, 그 시점 환율 없음)
     */
    public JsonNode get(
            BrokerCredentials credentials, String accountSeq, String path, Map<String, ?> query, boolean notFoundAsNull) {
        String token = tokens.get(credentials, this::issue);
        Raw raw = sendAuthorized(token, accountSeq, path, query);
        if (raw.status() == 401) {
            String code = errorCode(raw);
            if (!RETRYABLE_TOKEN_ERRORS.contains(code)) {
                throw new BrokerException(Reason.INVALID_CREDENTIALS, "토스 인증 실패(" + code + ")");
            }
            // 토큰은 클라이언트당 하나라 다른 곳에서 같은 키로 새 토큰을 받으면 우리 토큰이 무효가 된다. 한 번만 다시 받는다.
            tokens.invalidate(credentials.clientId(), token);
            token = tokens.get(credentials, this::issue);
            raw = sendAuthorized(token, accountSeq, path, query);
            if (raw.status() == 401) {
                tokens.invalidate(credentials.clientId(), token);
                throw new BrokerException(Reason.TOKEN_REVOKED,
                        "토큰을 다시 받아도 무효가 됩니다(" + errorCode(raw) + "). 다른 곳에서 같은 키를 쓰고 있을 수 있습니다.");
            }
        }
        if (raw.ok()) {
            JsonNode body = parse(raw.body());
            JsonNode result = body.path("result");
            if (result.isMissingNode() || result.isNull()) {
                // 형식이 다르면 빈 값(0종목·0원)으로 조용히 넘어가지 않게 실패시킨다. 값은 빼고 키 이름만 남긴다.
                log.warn("토스 응답에 result가 없습니다: {} 최상위 키 {}", path, fieldNames(body));
                throw new BrokerException(Reason.UNAVAILABLE, "토스 응답 형식이 예상과 다릅니다(" + path + ")");
            }
            log.debug("토스 응답 {}: result 키 {}", path, fieldNames(result));
            return result;
        }
        if (raw.status() == 404 && notFoundAsNull) {
            return null;
        }
        throw failure(raw, path);
    }

    /** 토큰 발급. 실패는 사유별 BrokerException. */
    TossTokenCache.Token issue(BrokerCredentials credentials) {
        String form = "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(credentials.clientId(), StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(credentials.clientSecret(), StandardCharsets.UTF_8);
        Raw raw = send(HttpMethod.POST, TossOutboundGuard.TOKEN_PATH, Map.of(), null, form);
        if (raw.ok()) {
            JsonNode body = parse(raw.body());
            String accessToken = body.path("access_token").asString(null);
            long expiresIn = body.path("expires_in").asLong(0);
            if (accessToken == null || accessToken.isBlank()) {
                throw new BrokerException(Reason.UNAVAILABLE, "토스 토큰 응답에 access_token이 없습니다");
            }
            log.debug("토스 토큰 발급: clientId={}…, 만료 {}초", prefix(credentials.clientId()), expiresIn);
            return new TossTokenCache.Token(accessToken, clock.instant().plusSeconds(Math.max(expiresIn, 60)));
        }
        JsonNode errorBody = parseQuietly(raw.body());
        String error = errorBody.path("error").asString("");
        throw switch (raw.status()) {
            case 400, 401 -> new BrokerException(Reason.INVALID_CREDENTIALS, "토스 키 인증 실패(" + error + ")",
                    credentialField(errorBody.path("error_description").asString("")));
            case 403 -> new BrokerException(Reason.IP_NOT_ALLOWED, "허용되지 않은 IP에서 토큰을 요청했습니다(" + error + ")");
            default -> failure(raw, TossOutboundGuard.TOKEN_PATH);
        };
    }

    private Raw sendAuthorized(String token, String accountSeq, String path, Map<String, ?> query) {
        return send(HttpMethod.GET, path, query, headers -> {
            headers.setBearerAuth(token);
            if (accountSeq != null) {
                headers.set("X-Tossinvest-Account", accountSeq);
            }
        }, null);
    }

    private Raw send(HttpMethod method, String path, Map<String, ?> query, Consumer<HttpHeaders> headers,
            String form) {
        TossOutboundGuard.check(method, path);
        RateLimitGroup group = RateLimitGroup.of(path);
        Map<String, Object> vars = new LinkedHashMap<>();
        StringBuilder uri = new StringBuilder(trimSlash(baseUrl.get())).append(path);
        query.forEach((k, v) -> {
            if (v != null) {
                uri.append(vars.isEmpty() ? '?' : '&').append(k).append("={").append(k).append('}');
                vars.put(k, v.toString());
            }
        });
        for (int attempt = 0; ; attempt++) {
            limiter.acquire(group);
            Raw raw;
            try {
                RestClient.RequestBodySpec spec = http.method(method).uri(uri.toString(), vars);
                if (headers != null) {
                    spec.headers(headers);
                }
                if (form != null) {
                    spec.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form);
                }
                raw = spec.exchange((req, res) -> new Raw(
                        res.getStatusCode().value(),
                        new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8),
                        intHeader(res.getHeaders(), "Retry-After"),
                        intHeader(res.getHeaders(), "X-RateLimit-Remaining"),
                        intHeader(res.getHeaders(), "X-RateLimit-Reset")));
            } catch (ResourceAccessException e) {
                if (attempt < properties.maxRetries()) {
                    log.warn("토스 {} {} 연결 실패, 다시 시도 {}/{}", method, path, attempt + 1, properties.maxRetries());
                    pacer.sleep(backoff(attempt));
                    continue;
                }
                throw new BrokerException(Reason.UNAVAILABLE, "토스에 연결할 수 없습니다(" + path + ")", e);
            }
            limiter.observe(group, raw.remaining(), raw.reset());
            log.debug("토스 {} {} → {}", method, path, raw.status());
            if (raw.status() == 429) {
                Duration wait = Duration.ofSeconds(raw.retryAfter() == null || raw.retryAfter() <= 0 ? 1 : raw.retryAfter());
                limiter.pause(group, wait);
                if (attempt < properties.maxRetries()) {
                    log.info("토스 {} 한도 초과, {}초 뒤 다시 시도 {}/{}", group, wait.toSeconds(), attempt + 1, properties.maxRetries());
                    pacer.sleep(wait);
                    continue;
                }
                throw new BrokerException(Reason.RATE_LIMITED, "토스 요청 한도를 넘었습니다(" + group + ")");
            }
            if (raw.status() >= 500 && attempt < properties.maxRetries()) {
                log.warn("토스 {} {} → {}, 다시 시도 {}/{}", method, path, raw.status(), attempt + 1, properties.maxRetries());
                pacer.sleep(backoff(attempt));
                continue;
            }
            return raw;
        }
    }

    private BrokerException failure(Raw raw, String path) {
        String code = errorCode(raw);
        if (raw.status() == 403) {
            return code.contains("ip") || code.equals("access_denied")
                    ? new BrokerException(Reason.IP_NOT_ALLOWED, "허용되지 않은 IP에서 요청했습니다(" + code + ")")
                    : new BrokerException(Reason.INVALID_CREDENTIALS, "토스 권한이 없습니다(" + code + ")");
        }
        if (raw.status() == 401) {
            return new BrokerException(Reason.INVALID_CREDENTIALS, "토스 인증 실패(" + code + ")");
        }
        if (raw.status() == 429) {
            return new BrokerException(Reason.RATE_LIMITED, "토스 요청 한도를 넘었습니다");
        }
        return new BrokerException(Reason.UNAVAILABLE, "토스 " + path + " 응답 " + raw.status() + "(" + code + ")");
    }

    /** 일반 API는 {@code error.code}, 토큰 API는 {@code error}(OAuth2 형식). */
    private static String errorCode(Raw raw) {
        JsonNode error = parseQuietly(raw.body()).path("error");
        if (error.isString()) {
            return error.asString();
        }
        return error.path("code").asString("");
    }

    private static JsonNode parse(String body) {
        try {
            return JSON.readTree(body);
        } catch (JacksonException e) {
            throw new BrokerException(Reason.UNAVAILABLE, "토스 응답을 읽을 수 없습니다", e);
        }
    }

    private static JsonNode parseQuietly(String body) {
        try {
            return body == null || body.isBlank() ? JSON.missingNode() : JSON.readTree(body);
        } catch (JacksonException e) {
            return JSON.missingNode();
        }
    }

    private static Integer intHeader(HttpHeaders headers, String name) {
        String v = headers.getFirst(name);
        if (v == null) {
            return null;
        }
        try {
            return Integer.valueOf(v.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Duration backoff(int attempt) {
        return BACKOFF_BASE.multipliedBy(1L << attempt);
    }

    private static String trimSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String prefix(String clientId) {
        return clientId.length() <= 6 ? clientId : clientId.substring(0, 6);
    }

    /** 로그용: 값 없이 키 이름만. */
    private static java.util.List<String> fieldNames(JsonNode node) {
        java.util.List<String> names = new java.util.ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    /**
     * 토스 FAQ: error_description에 client_id와 client_secret 중 어느 쪽 검증이 실패했는지 나온다
     * (예: "Client authentication failed: client_secret", "OAuth 2.0 Parameter: client_secret").
     */
    static String credentialField(String description) {
        if (description.contains("client_secret")) {
            return "clientSecret";
        }
        if (description.contains("client_id")) {
            return "clientId";
        }
        return null;
    }
}
