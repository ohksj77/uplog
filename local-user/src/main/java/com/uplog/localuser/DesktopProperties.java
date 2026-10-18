package com.uplog.localuser;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code uplog.desktop.*}: Electron이 엔진을 띄울 때 넘기는 값(docs/design/desktop-app.md "실행 계약").
 *
 * @param launchToken 실행마다 새로 만든 비밀 토큰(256비트 base64url). {@code /__launch}에서 쿠키로 바꿔 준다.
 *     dev-open이 아니면 필수(없으면 시작하지 않는다)
 * @param dataDir H2 파일 DB({@code {dataDir}/uplog.mv.db}), 로그({@code {dataDir}/logs/engine.log}), 키 파일(키체인이 없을 때) 위치
 * @param webDir web 정적 export 폴더. 비어 있으면 화면을 서빙하지 않는다(엔진만)
 * @param devOpen 개발용: 토큰·Host 검사를 끈다(127.0.0.1 바인딩은 그대로). {@code next dev}가 {@code /api}를 넘길 때
 * @param devOrigins dev-open일 때 상태를 바꾸는 요청에 더 허용하는 출처({@code next dev})
 */
@ConfigurationProperties("uplog.desktop")
public record DesktopProperties(
        String launchToken,
        String dataDir,
        String webDir,
        @DefaultValue("false") boolean devOpen,
        @DefaultValue({"http://localhost:3000", "http://127.0.0.1:3000"}) List<String> devOrigins) {

    /** 실행 토큰의 최소 길이(256비트 base64url은 43자). 짧은 토큰은 추측될 수 있어 거부한다. */
    static final int MIN_TOKEN_LENGTH = 32;

    public DesktopProperties {
        devOrigins = List.copyOf(devOrigins);
    }

    public boolean hasLaunchToken() {
        return launchToken != null && !launchToken.isBlank();
    }

    @Override
    public String toString() {
        return "DesktopProperties[launchToken=***, dataDir=" + dataDir + ", webDir=" + webDir + ", devOpen=" + devOpen
                + ", devOrigins=" + devOrigins + "]";
    }
}
