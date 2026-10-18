package com.uplog.localuser;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 로컬 엔진 보안(docs/design/desktop-app.md "로컬 엔진 보안"). 필터 순서: Host 검사 → Origin 검사 → 실행 쿠키 검사.
 * 상태 없음(세션 없음), 401·403은 JSON(리다이렉트 없음). 시작할 때 실행 토큰과 바인딩 주소를 확인한다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableWebSecurity
@EnableConfigurationProperties(DesktopProperties.class)
public class DesktopSecurityConfiguration {

    /** 가짜 토스 서버(fake-toss). 엔진 자신이 부르는 경로라 Origin 검사에서 뺀다. */
    static final String FAKE_TOSS = "/__fake-toss/";
    static final String LOOPBACK = "127.0.0.1";

    private static final Logger log = LoggerFactory.getLogger(DesktopSecurityConfiguration.class);

    @Bean
    LaunchToken launchToken(DesktopProperties properties, Environment environment) {
        validate(properties, environment.getProperty("server.address"));
        if (properties.devOpen()) {
            log.warn("uplog.desktop.dev-open=true: 실행 토큰·Host 검사를 끕니다(개발용). 127.0.0.1에만 바인딩합니다.");
        }
        return new LaunchToken(properties.launchToken());
    }

    /**
     * 시작 전 확인. 127.0.0.1이 아닌 주소에 바인딩하려 하거나, dev-open이 아닌데 실행 토큰이 없거나 짧으면 시작하지 않는다.
     *
     * @throws IllegalStateException 값은 메시지에 넣지 않는다
     */
    static void validate(DesktopProperties properties, String serverAddress) {
        if (!LOOPBACK.equals(serverAddress == null ? null : serverAddress.strip())) {
            throw new IllegalStateException("로컬 엔진은 127.0.0.1에만 바인딩합니다(server.address=127.0.0.1).");
        }
        if (!properties.hasLaunchToken()) {
            if (!properties.devOpen()) {
                throw new IllegalStateException(
                        "uplog.desktop.launch-token이 없습니다. Electron이 실행마다 만든 토큰을 넘겨야 합니다(개발: --uplog.desktop.dev-open=true).");
            }
        } else if (properties.launchToken().strip().length() < DesktopProperties.MIN_TOKEN_LENGTH) {
            throw new IllegalStateException(
                    "uplog.desktop.launch-token이 너무 짧습니다(" + DesktopProperties.MIN_TOKEN_LENGTH + "자 이상, 256비트 base64url 권장).");
        }
    }

    @Bean
    SecurityFilterChain uplogSecurityFilterChain(
            HttpSecurity http, DesktopProperties properties, LaunchToken token, LocalUsers users) {
        List<String> allowedOrigins = properties.devOpen() ? properties.devOrigins() : List.of();
        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> DesktopErrors.locked(response))
                        .accessDeniedHandler((request, response, ex) -> DesktopErrors.locked(response)))
                .addFilterBefore(new OriginCheckFilter(allowedOrigins, List.of(FAKE_TOSS)), CsrfFilter.class)
                .addFilterBefore(new DesktopGateFilter(token, properties.devOpen(), users), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        if (!properties.devOpen()) {
            http.addFilterBefore(new HostCheckFilter(), SecurityContextHolderFilter.class);
        }
        return http.build();
    }

    /**
     * 헤더 값에 한글 같은 비ASCII가 있어도 받는다. 기본 방화벽은 Latin-1로 읽힌 UTF-8 바이트(0x80~0x9F)를 제어 문자로 보고
     * 400을 준다(Electron 기본 User-Agent의 앱 이름 등). 제어 문자는 여전히 막는다.
     */
    @Bean
    WebSecurityCustomizer headerValueFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowedHeaderValues(DesktopSecurityConfiguration::allowedHeaderValue);
        return web -> web.httpFirewall(firewall);
    }

    static boolean allowedHeaderValue(String value) {
        return value.chars().noneMatch(c -> c < 0x20 && c != '\t' || c == 0x7F);
    }

    /** 아이디·비밀번호 로그인은 없다. 이 빈이 있으면 스프링이 기본 사용자와 무작위 비밀번호를 만들지 않는다. */
    @Bean
    UserDetailsService noUserDetails() {
        return username -> {
            throw new UsernameNotFoundException("로그인 없음");
        };
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
}
