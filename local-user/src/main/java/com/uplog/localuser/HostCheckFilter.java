package com.uplog.localuser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * DNS 리바인딩 차단. {@code Host}가 {@code 127.0.0.1:{port}} 또는 {@code localhost:{port}}가 아니면 403이다.
 * {port}는 요청이 실제로 들어온 포트다. Host가 없거나 다른 이름(공격자 도메인이 127.0.0.1로 풀린 경우)이면 거부한다.
 */
final class HostCheckFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!allowed(request.getHeader(HttpHeaders.HOST), request.getLocalPort())) {
            DesktopErrors.hostRejected(response);
            return;
        }
        chain.doFilter(request, response);
    }

    static boolean allowed(String host, int port) {
        if (host == null) {
            return false;
        }
        String h = host.strip().toLowerCase(Locale.ROOT);
        if (h.equals("127.0.0.1:" + port) || h.equals("localhost:" + port)) {
            return true;
        }
        // 포트를 생략한 Host는 기본 포트(80)로 들어왔을 때만
        return port == 80 && (h.equals("127.0.0.1") || h.equals("localhost"));
    }
}
