package com.uplog.auth;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code uplog.auth.*}.
 *
 * @param sessionSecret 세션 JWT(HS256) 서명 키, 32바이트 이상. 비어 있으면 실행마다 무작위 키를 만든다(재시작하면 모두 로그아웃)
 * @param secureCookie 세션 쿠키에 Secure를 붙인다(운영 HTTPS)
 * @param sessionTtl 세션 쿠키·토큰 유효 기간
 * @param allowedOrigins 상태를 바꾸는 요청(POST·PUT·PATCH·DELETE)에 허용하는 {@code Origin}. 요청 자신의 출처는 항상 허용한다
 * @param kakao 카카오 OAuth2. client-id와 client-secret이 모두 있을 때만 카카오 로그인을 켠다
 */
@ConfigurationProperties("uplog.auth")
public record AuthProperties(
        String sessionSecret,
        @DefaultValue("false") boolean secureCookie,
        @DefaultValue("14d") Duration sessionTtl,
        @DefaultValue("http://localhost:3000") List<String> allowedOrigins,
        @DefaultValue Kakao kakao) {

    public AuthProperties {
        allowedOrigins = List.copyOf(allowedOrigins);
    }

    /** @param redirectUri 카카오 개발자 콘솔에 등록한 Redirect URI. {@code {baseUrl}}은 요청 기준으로 채워진다 */
    public record Kakao(
            String clientId,
            String clientSecret,
            @DefaultValue("{baseUrl}/login/oauth2/code/{registrationId}") String redirectUri) {

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }

        @Override
        public String toString() {
            return "Kakao[clientId=" + clientId + ", clientSecret=***, redirectUri=" + redirectUri + "]";
        }
    }

    @Override
    public String toString() {
        return "AuthProperties[sessionSecret=***, secureCookie=" + secureCookie + ", sessionTtl=" + sessionTtl
                + ", allowedOrigins=" + allowedOrigins + ", kakao=" + kakao + "]";
    }
}
