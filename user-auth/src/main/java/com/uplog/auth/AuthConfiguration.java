package com.uplog.auth;

import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 보안 구성. 상태 없는 세션(서명한 JWT 쿠키), {@code Origin} 검사로 CSRF 방어, 401·403은 JSON(리다이렉트 없음).
 *
 * <p>공개: 시뮬레이션·ETF·포트폴리오(비로그인은 데모 계좌)·{@code /api/me}·{@code /api/auth/**}·헬스 체크.
 * 그 밖의 {@code /api/**}는 로그인이 필요하다(새 엔드포인트는 기본으로 잠긴다).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {

    /** local 프로필의 가짜 토스 서버(백엔드 B). 서버 간 호출이라 Origin 검사에서 빼고, 다른 프로필에서는 막는다. */
    static final String FAKE_TOSS = "/__fake-toss/";

    @Bean
    SessionTokens sessionTokens(AuthProperties properties) {
        return SessionTokens.create(properties, Clock.systemUTC());
    }

    @Bean
    SessionCookies sessionCookies(SessionTokens tokens, AuthProperties properties) {
        return new SessionCookies(tokens, properties.secureCookie());
    }

    @Bean
    @Conditional(KakaoConfigured.class)
    ClientRegistrationRepository kakaoClientRegistrationRepository(AuthProperties properties) {
        return new InMemoryClientRegistrationRepository(KakaoLogin.registration(properties.kakao()));
    }

    @Bean
    SecurityFilterChain uplogSecurityFilterChain(
            HttpSecurity http,
            AuthProperties properties,
            SessionTokens tokens,
            SessionCookies cookies,
            UserRepository users,
            ObjectProvider<ClientRegistrationRepository> clients,
            Environment environment) {
        boolean local = environment.acceptsProfiles(Profiles.of("local"));
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> AuthErrors.unauthenticated(response))
                        .accessDeniedHandler((request, response, ex) -> AuthErrors.forbidden(response)))
                .addFilterBefore(new OriginCheckFilter(properties.allowedOrigins(), local ? List.of(FAKE_TOSS) : List.of()), CsrfFilter.class)
                .addFilterBefore(new SessionAuthenticationFilter(cookies, users), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/**", "/api/me", "/api/portfolio/**", "/api/simulations/**", "/api/etfs/**")
                        .permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(FAKE_TOSS + "**").access((auth, ctx) -> new AuthorizationDecision(local))
                        .requestMatchers("/oauth2/**", "/login/oauth2/**", "/error").permitAll()
                        .requestMatchers("/api/**", "/actuator/**").authenticated()
                        .anyRequest().permitAll());
        ClientRegistrationRepository registrations = clients.getIfAvailable();
        if (registrations != null) {
            KakaoLogin kakao = new KakaoLogin(users, cookies, new CookieAuthorizationRequestRepository(tokens, cookies));
            http.oauth2Login(o -> o
                    .clientRegistrationRepository(registrations)
                    .authorizedClientRepository(KakaoLogin.noAuthorizedClientStore())
                    .authorizationEndpoint(ae -> ae.authorizationRequestRepository(kakao.requestRepository()))
                    .successHandler(kakao.successHandler())
                    .failureHandler(kakao.failureHandler()));
        }
        return http.build();
    }

    @Bean
    WebMvcConfigurer currentUserArgumentResolver() {
        return new WebMvcConfigurer() {
            @Override
            public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
                resolvers.add(new CurrentUserArgumentResolver());
            }
        };
    }

    /** {@code uplog.auth.kakao.client-id}와 {@code client-secret}이 모두 있을 때. 값은 조건식에 넣지 않는다(로그 노출 방지). */
    static final class KakaoConfigured implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Binder binder = Binder.get(context.getEnvironment());
            String id = binder.bind("uplog.auth.kakao.client-id", String.class).orElse("");
            String secret = binder.bind("uplog.auth.kakao.client-secret", String.class).orElse("");
            return !id.isBlank() && !secret.isBlank();
        }
    }
}
