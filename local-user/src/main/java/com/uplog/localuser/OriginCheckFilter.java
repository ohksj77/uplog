package com.uplog.localuser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 실행 쿠키의 CSRF 방어. 상태를 바꾸는 요청(POST·PUT·PATCH·DELETE)은 {@code Origin}(없으면 {@code Referer})의 출처가
 * 요청 자신의 출처(같은 출처)여야 한다. 허용 목록은 dev-open의 {@code next dev} 출처뿐이다. 둘 다 없는 요청은 실행 쿠키가
 * 없을 때만 통과시킨다(브라우저는 이런 요청에 Origin을 붙이므로, 쿠키를 들고 Origin 없이 오는 요청은 믿지 않는다).
 */
final class OriginCheckFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final Set<String> allowed;
    private final List<String> exemptPrefixes;

    /** @param exemptPrefixes 브라우저가 부르지 않는 엔진 안의 경로(가짜 토스 서버) */
    OriginCheckFilter(List<String> allowedOrigins, List<String> exemptPrefixes) {
        this.allowed = Set.copyOf(allowedOrigins.stream().map(OriginCheckFilter::normalize).toList());
        this.exemptPrefixes = List.copyOf(exemptPrefixes);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return exemptPrefixes.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) {
            origin = originOf(request.getHeader(HttpHeaders.REFERER));
        }
        boolean ok = origin == null
                ? !LaunchToken.hasCookie(request)
                : isAllowed(normalize(origin), request);
        if (!ok) {
            DesktopErrors.originRejected(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isAllowed(String origin, HttpServletRequest request) {
        if (allowed.contains(origin)) {
            return true;
        }
        int port = request.getServerPort();
        String scheme = request.getScheme();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        String self = scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
        return origin.equals(normalize(self));
    }

    private static String originOf(String referer) {
        if (referer == null) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return "invalid:";
            }
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException e) {
            return "invalid:";
        }
    }

    private static String normalize(String origin) {
        String o = origin.trim().toLowerCase(Locale.ROOT);
        return o.endsWith("/") ? o.substring(0, o.length() - 1) : o;
    }
}
