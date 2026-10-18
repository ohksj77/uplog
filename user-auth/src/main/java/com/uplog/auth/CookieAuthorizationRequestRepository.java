package com.uplog.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * OAuth2 인가 요청을 HTTP 세션 대신 서명한 단기 쿠키({@code uplog_oauth2})에 둔다. 서버는 상태를 갖지 않는다.
 * 카카오에서 돌아오는 요청은 최상위 GET 이동이라 SameSite=Lax 쿠키가 따라온다.
 */
final class CookieAuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE = "uplog_oauth2";
    private static final String USE = "oauth2-request";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final SessionTokens tokens;
    private final SessionCookies cookies;

    CookieAuthorizationRequestRepository(SessionTokens tokens, SessionCookies cookies) {
        this.tokens = tokens;
        this.cookies = cookies;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter(OAuth2ParameterNames.STATE);
        if (state == null) {
            return null;
        }
        return SessionCookies.read(request, COOKIE)
                .flatMap(token -> tokens.verify(USE, token))
                .flatMap(CookieAuthorizationRequestRepository::fromJwt)
                .filter(r -> state.equals(r.getState()))
                .orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest, HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            cookies.set(response, COOKIE, "", Duration.ZERO);
            return;
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("au", authorizationRequest.getAuthorizationUri());
        claims.put("ci", authorizationRequest.getClientId());
        claims.put("ru", authorizationRequest.getRedirectUri());
        claims.put("sc", List.copyOf(authorizationRequest.getScopes()));
        claims.put("st", authorizationRequest.getState());
        claims.put("aru", authorizationRequest.getAuthorizationRequestUri());
        claims.put("at", stringMap(authorizationRequest.getAttributes()));
        claims.put("ap", stringMap(authorizationRequest.getAdditionalParameters()));
        cookies.set(response, COOKIE, tokens.issue(USE, "oauth2", TTL, claims), TTL);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        OAuth2AuthorizationRequest loaded = loadAuthorizationRequest(request);
        cookies.set(response, COOKIE, "", Duration.ZERO);
        return loaded;
    }

    private static Map<String, String> stringMap(Map<String, Object> source) {
        Map<String, String> m = new HashMap<>();
        source.forEach((k, v) -> m.put(k, String.valueOf(v)));
        return m;
    }

    private static Optional<OAuth2AuthorizationRequest> fromJwt(Jwt jwt) {
        try {
            Map<String, Object> attributes = jwt.getClaimAsMap("at");
            Map<String, Object> additional = jwt.getClaimAsMap("ap");
            return Optional.of(OAuth2AuthorizationRequest.authorizationCode()
                    .authorizationUri(jwt.getClaimAsString("au"))
                    .clientId(jwt.getClaimAsString("ci"))
                    .redirectUri(jwt.getClaimAsString("ru"))
                    .scopes(new LinkedHashSet<>(jwt.getClaimAsStringList("sc")))
                    .state(jwt.getClaimAsString("st"))
                    .authorizationRequestUri(jwt.getClaimAsString("aru"))
                    .attributes(a -> a.putAll(attributes == null ? Map.of() : attributes))
                    .additionalParameters(p -> p.putAll(additional == null ? Map.of() : additional))
                    .build());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
