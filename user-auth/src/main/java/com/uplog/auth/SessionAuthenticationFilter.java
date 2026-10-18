package com.uplog.auth;

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
 * 세션 쿠키의 JWT를 검증해 {@link CurrentUser}를 보안 컨텍스트에 넣는다. 없거나 틀리거나 사용자가 없어졌으면
 * (예: local 메모리 DB 재시작) 비로그인으로 둔다.
 */
final class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final SessionCookies cookies;
    private final UserRepository users;

    SessionAuthenticationFilter(SessionCookies cookies, UserRepository users) {
        this.cookies = cookies;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        cookies.userId(request).filter(users::exists).ifPresent(id -> {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    new CurrentUser(id), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            SecurityContextHolder.setContext(context);
        });
        chain.doFilter(request, response);
    }
}
