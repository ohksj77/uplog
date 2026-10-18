package com.uplog.auth;

import com.uplog.auth.AuthErrors.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** local 프로필 전용 개발용 로그인. 같은 닉네임이면 같은 사용자다. 다른 프로필에는 이 엔드포인트가 없다(404). */
@RestController
@Profile("local")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DevLoginController {

    public record DevLoginRequest(String nickname) {}

    static final int NICKNAME_MAX = 20;

    private final UserRepository users;
    private final SessionCookies cookies;

    public DevLoginController(UserRepository users, SessionCookies cookies) {
        this.users = users;
        this.cookies = cookies;
    }

    @PostMapping("/api/auth/dev-login")
    public ResponseEntity<?> devLogin(@RequestBody(required = false) DevLoginRequest request, HttpServletResponse response) {
        String nickname = request == null || request.nickname() == null ? "" : request.nickname().strip();
        if (nickname.isEmpty() || nickname.length() > NICKNAME_MAX) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("INVALID_PARAMETER", "닉네임은 1–" + NICKNAME_MAX + "자로 입력해 주세요."));
        }
        AuthUser user = users.upsert(UserRepository.DEV, nickname, nickname);
        cookies.login(response, user.id());
        return ResponseEntity.ok(user);
    }
}
