package com.uplog.localuser;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 이 요청을 보낸 로컬 사용자. 컨트롤러 파라미터로 받는다. 실행 쿠키가 맞는(또는 dev-open) {@code /api/**} 요청에는 늘 있다.
 *
 * <ul>
 *   <li>{@code CurrentUser user}: 필수. 없으면 403 {@code DESKTOP_LOCKED}
 *   <li>{@code Optional<CurrentUser> user}: 없으면 빈 값
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
