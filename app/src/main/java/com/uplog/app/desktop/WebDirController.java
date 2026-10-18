package com.uplog.app.desktop;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.server.ResponseStatusException;

/**
 * {@code uplog.desktop.web-dir}의 web 정적 export를 서빙한다(같은 출처라 CORS·쿠키 문제가 없다).
 *
 * <p>찾는 순서: 정확한 파일 → {@code {path}.html} → {@code {path}/index.html} → {@code 404.html}(상태 404). 폴더 자체는
 * 파일이 아니라서 건너뛴다(Next export는 {@code connect.html} 옆에 {@code __next.*.txt}만 든 {@code connect/} 폴더를 둔다).
 * {@code _next/static/**}은 파일 이름에 해시가 있어 1년 캐시, 그 밖은 매번 확인({@code no-cache}). {@code .txt}(RSC 페이로드)는
 * {@code text/plain; charset=utf-8}(Next 라우터가 {@code __next.*.txt}를 받아 간다). {@code /api/**}는 여기서 다루지 않는다(404).
 * {@code /__health}·{@code /__launch}는 자기 컨트롤러가 먼저 받는다. web-dir 밖의 경로는 거부한다.
 */
@Controller
@ConditionalOnExpression("'${uplog.desktop.web-dir:}' != ''")
public class WebDirController {

    static final CacheControl IMMUTABLE = CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable();

    private final Path root;

    public WebDirController(org.springframework.core.env.Environment environment) throws IOException {
        this.root = Path.of(environment.getRequiredProperty("uplog.desktop.web-dir")).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("uplog.desktop.web-dir 폴더가 없습니다: " + root);
        }
    }

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<Resource> serve(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/api") || path.startsWith("/api/")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Optional<Path> found = resolve(path);
        if (found.isPresent()) {
            Path file = found.get();
            boolean hashed = path.startsWith("/_next/static/");
            return ResponseEntity.ok()
                    .cacheControl(hashed ? IMMUTABLE : CacheControl.noCache())
                    .contentType(contentType(file))
                    .body(new FileSystemResource(file));
        }
        Path notFound = root.resolve("404.html");
        if (Files.isRegularFile(notFound)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .cacheControl(CacheControl.noCache())
                    .contentType(contentType(notFound))
                    .body(new FileSystemResource(notFound));
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    /** 정확한 파일 → {path}.html → {path}/index.html. web-dir 밖으로 나가는 경로는 찾지 않는다. */
    Optional<Path> resolve(String path) {
        String relative = path.startsWith("/") ? path.substring(1) : path;
        if (relative.contains("\\") || relative.contains("\0")) {
            return Optional.empty();
        }
        if (relative.endsWith("/")) {
            relative = relative.substring(0, relative.length() - 1);
        }
        String[] candidates = relative.isEmpty()
                ? new String[] {"index.html"}
                : new String[] {relative, relative + ".html", relative + "/index.html"};
        for (String candidate : candidates) {
            Path file;
            try {
                file = root.resolve(candidate).normalize();
            } catch (InvalidPathException e) {
                return Optional.empty();
            }
            if (file.startsWith(root) && Files.isRegularFile(file)) {
                return Optional.of(file);
            }
        }
        return Optional.empty();
    }

    static MediaType contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".txt")) {
            return new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8);
        }
        if (name.endsWith(".html")) {
            return new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8);
        }
        return MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }
}
