package com.uplog.auth;

import com.uplog.auth.AuthErrors.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 로그아웃과 쓸 수 있는 로그인 수단. 카카오 로그인 시작은 {@code GET /oauth2/authorization/kakao}(키가 설정됐을 때만). */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthController {

    public record Providers(boolean kakao, boolean dev) {}

    /** 다른 기기에서 이어 하기 코드. token은 이 응답에서만 보인다. */
    public record HandoffResponse(String token, java.time.Instant expiresAt) {}

    public record RedeemRequest(String token) {}

    public record RedeemResponse(String nickname) {}

    private final SessionCookies cookies;
    private final ObjectProvider<ClientRegistrationRepository> clients;
    private final Environment environment;
    private final LoginHandoff handoff;
    private final UserRepository users;

    public AuthController(
            SessionCookies cookies,
            ObjectProvider<ClientRegistrationRepository> clients,
            Environment environment,
            LoginHandoff handoff,
            UserRepository users) {
        this.cookies = cookies;
        this.clients = clients;
        this.environment = environment;
        this.handoff = handoff;
        this.users = users;
    }

    /** 로그인한 기기에서 다른 기기(주로 PC)로 이어 갈 1회용 코드를 만든다(10분, 1회). */
    @PostMapping("/handoff")
    public HandoffResponse handoff(CurrentUser user) {
        LoginHandoff.Issued issued = handoff.issue(user.id());
        return new HandoffResponse(issued.token(), issued.expiresAt());
    }

    /** 이어 하기 코드를 내면 같은 사용자의 세션 쿠키를 준다. */
    @PostMapping("/handoff/redeem")
    public ResponseEntity<?> redeem(
            @org.springframework.web.bind.annotation.RequestBody(required = false) RedeemRequest request,
            HttpServletResponse response) {
        java.util.Optional<AuthUser> user = handoff.redeem(request == null ? null : request.token()).flatMap(users::find);
        if (user.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("HANDOFF_INVALID",
                    "이어 하기 링크가 만료됐거나 이미 쓰였어요. 처음 기기에서 새 링크를 만들어 주세요."));
        }
        cookies.login(response, user.get().id());
        return ResponseEntity.ok(new RedeemResponse(user.get().nickname()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletResponse response) {
        cookies.logout(response);
    }

    @GetMapping("/providers")
    public Providers providers() {
        ClientRegistrationRepository registrations = clients.getIfAvailable();
        boolean kakao = registrations != null && registrations.findByRegistrationId(KakaoLogin.REGISTRATION_ID) != null;
        boolean dev = Arrays.asList(environment.getActiveProfiles()).contains("local");
        return new Providers(kakao, dev);
    }

    /** 컨트롤러에서 {@link CurrentUser}가 필요한데 비로그인이면 401. */
    @RestControllerAdvice
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public static class UnauthenticatedHandler {

        @ExceptionHandler(UnauthenticatedException.class)
        public ResponseEntity<ErrorResponse> handle(UnauthenticatedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("UNAUTHENTICATED", e.getMessage()));
        }
    }
}
