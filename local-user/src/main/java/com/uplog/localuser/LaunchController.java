package com.uplog.localuser;

import com.uplog.localuser.DesktopErrors.ErrorResponse;
import java.net.URI;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Electron이 쓰는 두 경로: 준비 확인({@code /__health})과 실행 토큰 → 쿠키({@code /__launch}). */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class LaunchController {

    private final DesktopProperties properties;
    private final LaunchToken token;

    public LaunchController(DesktopProperties properties, LaunchToken token) {
        this.properties = properties;
        this.token = token;
    }

    @GetMapping("/__health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    /**
     * 토큰이 맞으면 실행 쿠키를 주고 {@code /}로 302. 틀리면 403 {@code DESKTOP_LOCKED}.
     * dev-open이고 토큰을 넣지 않은 실행이면 쿠키 없이 {@code /}로 보낸다(어차피 검사하지 않는다).
     * 주소창의 토큰이 다른 곳에 새지 않게 캐시·Referer를 막는다.
     */
    @GetMapping("/__launch")
    public ResponseEntity<?> launch(@RequestParam(required = false) String token) {
        boolean ok = this.token.matches(token);
        if (!ok && !(properties.devOpen() && !this.token.configured())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .cacheControl(CacheControl.noStore())
                    .body(new ErrorResponse(DesktopErrors.DESKTOP_LOCKED, "실행 토큰이 맞지 않아요. 업로그 앱을 다시 열어 주세요."));
        }
        ResponseEntity.BodyBuilder redirect = ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/"))
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer");
        if (ok) {
            redirect.header(HttpHeaders.SET_COOKIE, LaunchToken.cookie(token).toString());
        }
        return redirect.build();
    }
}
