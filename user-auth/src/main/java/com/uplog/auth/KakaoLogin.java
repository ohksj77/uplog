package com.uplog.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * 카카오 로그인 구성품. 성공하면 사용자·외부 계정을 만들거나 찾고, 세션 쿠키를 내려 준 뒤 {@code /}로 보낸다.
 * 카카오 액세스 토큰은 회원 정보 조회 한 번에만 쓰고 저장하지 않는다.
 */
final class KakaoLogin {

    static final String REGISTRATION_ID = "kakao";

    private static final Logger log = LoggerFactory.getLogger(KakaoLogin.class);

    private final UserRepository users;
    private final SessionCookies cookies;
    private final CookieAuthorizationRequestRepository requests;

    KakaoLogin(UserRepository users, SessionCookies cookies, CookieAuthorizationRequestRepository requests) {
        this.users = users;
        this.cookies = cookies;
        this.requests = requests;
    }

    static ClientRegistration registration(AuthProperties.Kakao kakao) {
        return ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(kakao.clientId())
                .clientSecret(kakao.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(kakao.redirectUri())
                .scope("profile_nickname")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id")
                .clientName("Kakao")
                .build();
    }

    CookieAuthorizationRequestRepository requestRepository() {
        return requests;
    }

    AuthenticationSuccessHandler successHandler() {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            OAuth2User user = (OAuth2User) authentication.getPrincipal();
            AuthUser saved = users.upsert(UserRepository.KAKAO, user.getName(), nickname(user.getAttributes()));
            cookies.login(response, saved.id());
            response.sendRedirect("/");
        };
    }

    AuthenticationFailureHandler failureHandler() {
        return (request, response, exception) -> {
            log.warn("카카오 로그인 실패: {}", exception.getClass().getSimpleName());
            response.sendRedirect("/?loginError=kakao");
        };
    }

    /** 액세스 토큰을 보관하지 않는다(로그인에만 쓰므로). */
    static OAuth2AuthorizedClientRepository noAuthorizedClientStore() {
        return new OAuth2AuthorizedClientRepository() {
            @Override
            public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
                    String clientRegistrationId, Authentication principal, HttpServletRequest request) {
                return null;
            }

            @Override
            public void saveAuthorizedClient(
                    OAuth2AuthorizedClient authorizedClient, Authentication principal, HttpServletRequest request,
                    HttpServletResponse response) {}

            @Override
            public void removeAuthorizedClient(
                    String clientRegistrationId, Authentication principal, HttpServletRequest request,
                    HttpServletResponse response) {}
        };
    }

    /** properties.nickname, 없으면 kakao_account.profile.nickname. */
    static String nickname(Map<String, Object> attributes) {
        Object nickname = null;
        if (attributes.get("properties") instanceof Map<?, ?> properties) {
            nickname = properties.get("nickname");
        }
        if (nickname == null
                && attributes.get("kakao_account") instanceof Map<?, ?> account
                && account.get("profile") instanceof Map<?, ?> profile) {
            nickname = profile.get("nickname");
        }
        String s = nickname == null ? "" : nickname.toString().strip();
        return s.isEmpty() ? "카카오 사용자" : s;
    }
}
