package com.uplog.localuser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * {@code /api/**}는 실행 쿠키가 토큰과 맞아야 한다(없거나 틀리면 403 {@code DESKTOP_LOCKED}). 맞으면(또는 dev-open이면)
 * 로컬 사용자를 보안 컨텍스트에 넣는다. 그 밖의 경로(정적 화면, {@code /__health}, {@code /__launch})는 그대로 통과한다.
 */
final class DesktopGateFilter extends OncePerRequestFilter {

    private final LaunchToken token;
    private final boolean devOpen;
    private final LocalUsers users;

    DesktopGateFilter(LaunchToken token, boolean devOpen, LocalUsers users) {
        this.token = token;
        this.devOpen = devOpen;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/api") || path.startsWith("/api/")) {
            if (!devOpen && !token.matches(request)) {
                DesktopErrors.locked(response);
                return;
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    new CurrentUser(users.currentId()), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
}
