package com.uplog.auth;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 이 요청을 보낸 로그인 사용자. 컨트롤러 파라미터로 받는다.
 *
 * <ul>
 *   <li>{@code CurrentUser user}: 로그인 필수. 비로그인이면 401 {@code UNAUTHENTICATED}
 *   <li>{@code Optional<CurrentUser> user}: 비로그인이면 빈 값
 * </ul>
 */
public record CurrentUser(long id) {

    /** 지금 요청의 보안 컨텍스트에서 꺼낸다. */
    public static Optional<CurrentUser> fromContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CurrentUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }
}
