package com.uplog.localuser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 실행 토큰 비교, Host 허용 규칙, 시작 전 확인(바인딩 주소·토큰). */
class DesktopGuardsTest {

    private static final String TOKEN = "k3J9xQ2mV7pL0aZt8YwR5cN1bH6uE4sD0fG2jK9mQ7w";

    private static DesktopProperties props(String token, boolean devOpen) {
        return new DesktopProperties(token, "/tmp/d", "", devOpen, List.of());
    }

    @Test
    void 실행_토큰은_정확히_같을_때만_맞다() {
        LaunchToken token = new LaunchToken(TOKEN);
        assertThat(token.configured()).isTrue();
        assertThat(token.matches(TOKEN)).isTrue();
        assertThat(token.matches(TOKEN + "x")).isFalse();
        assertThat(token.matches(TOKEN.substring(1))).isFalse();
        assertThat(token.matches("")).isFalse();
        assertThat(token.matches((String) null)).isFalse();
        assertThat(token.toString()).doesNotContain(TOKEN);

        LaunchToken none = new LaunchToken("  ");
        assertThat(none.configured()).isFalse();
        assertThat(none.matches("  ")).isFalse();
        assertThat(none.matches("")).isFalse();
    }

    @Test
    void 실행_쿠키는_HttpOnly_SameSite_Strict_Path_루트이고_Secure가_없다() {
        String cookie = LaunchToken.cookie(TOKEN).toString();
        assertThat(cookie).startsWith("uplog_desktop=" + TOKEN).contains("HttpOnly", "SameSite=Strict", "Path=/")
                .doesNotContain("Secure", "Max-Age");
    }

    @Test
    void Host는_루프백_이름과_들어온_포트가_맞아야_한다() {
        assertThat(HostCheckFilter.allowed("127.0.0.1:51234", 51234)).isTrue();
        assertThat(HostCheckFilter.allowed("localhost:51234", 51234)).isTrue();
        assertThat(HostCheckFilter.allowed("LOCALHOST:51234", 51234)).isTrue();
        assertThat(HostCheckFilter.allowed("evil.com:51234", 51234)).isFalse();
        assertThat(HostCheckFilter.allowed("evil.com", 80)).isFalse();
        assertThat(HostCheckFilter.allowed("127.0.0.1:8080", 51234)).isFalse();
        assertThat(HostCheckFilter.allowed("127.0.0.1", 51234)).isFalse();
        assertThat(HostCheckFilter.allowed("127.0.0.1", 80)).isTrue();
        assertThat(HostCheckFilter.allowed("[::1]:51234", 51234)).isFalse();
        assertThat(HostCheckFilter.allowed(null, 51234)).isFalse();
    }

    @Test
    void 헤더_값은_비ASCII를_받고_제어_문자는_막는다() {
        String utf8AsLatin1 = new String("x 업로그/0.1.0 y".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(DesktopSecurityConfiguration.allowedHeaderValue(utf8AsLatin1)).isTrue();
        assertThat(DesktopSecurityConfiguration.allowedHeaderValue("Mozilla/5.0\tuplog/0.1.0")).isTrue();
        assertThat(DesktopSecurityConfiguration.allowedHeaderValue("a\r\nSet-Cookie: x")).isFalse();
        assertThat(DesktopSecurityConfiguration.allowedHeaderValue("a\u0000b")).isFalse();
        assertThat(DesktopSecurityConfiguration.allowedHeaderValue("a\u007Fb")).isFalse();
    }

    @Test
    void 바인딩은_127_0_0_1만_받는다() {
        for (String address : new String[] {null, "", "0.0.0.0", "192.168.0.10", "::", "localhost"}) {
            assertThatThrownBy(() -> DesktopSecurityConfiguration.validate(props(TOKEN, false), address))
                    .as(String.valueOf(address))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("127.0.0.1");
        }
        assertThatNoException().isThrownBy(() -> DesktopSecurityConfiguration.validate(props(TOKEN, false), "127.0.0.1"));
    }

    @Test
    void 토큰이_없거나_짧으면_dev_open이_아닐_때_시작하지_않는다() {
        assertThatThrownBy(() -> DesktopSecurityConfiguration.validate(props(null, false), "127.0.0.1"))
                .hasMessageContaining("launch-token");
        assertThatThrownBy(() -> DesktopSecurityConfiguration.validate(props("short-token", false), "127.0.0.1"))
                .hasMessageContaining("짧")
                .hasMessageNotContaining("short-token");
        assertThatThrownBy(() -> DesktopSecurityConfiguration.validate(props("short-token", true), "127.0.0.1"))
                .hasMessageContaining("짧");
        assertThatNoException().isThrownBy(() -> DesktopSecurityConfiguration.validate(props(null, true), "127.0.0.1"));
        assertThatThrownBy(() -> DesktopSecurityConfiguration.validate(props(null, true), "0.0.0.0"))
                .hasMessageContaining("127.0.0.1");
    }
}
