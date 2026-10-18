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

    private final SessionCookies cookies;
    private final ObjectProvider<ClientRegistrationRepository> clients;
    private final Environment environment;

    public AuthController(
            SessionCookies cookies, ObjectProvider<ClientRegistrationRepository> clients, Environment environment) {
        this.cookies = cookies;
        this.clients = clients;
        this.environment = environment;
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
