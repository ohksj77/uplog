package com.uplog.broker.toss;

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * 나가는 요청 허용 목록. 토스 키는 주문 권한까지 가지므로 토큰 발급과 조회용 GET 말고는 보내기 전에 막는다.
 * {@link TossClient}가 요청을 만들 때 한 번, 실제로 나가기 직전 인터셉터에서 최종 URI로 한 번 더 확인한다.
 */
public final class TossOutboundGuard implements ClientHttpRequestInterceptor {

    static final Set<String> GET_PATHS = Set.of(
            "/api/v1/accounts",
            "/api/v1/holdings",
            "/api/v1/orders",
            "/api/v1/buying-power",
            "/api/v1/exchange-rate",
            "/api/v1/candles",
            // 종목 유니버스·상세(기능 A 실제 종목 검색). 둘 다 시세 조회용 GET이다.
            "/api/v1/stocks",
            "/api/v1/stocks/all");
    static final String TOKEN_PATH = "/oauth2/token";
    private static final Pattern MARKET_CALENDAR = Pattern.compile("/api/v1/market-calendar/[A-Z]{2}");

    /** 허용 목록 밖의 요청. 보내지 않았다. */
    public static final class BlockedRequestException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        BlockedRequestException(String message) {
            super(message);
        }
    }

    private final Supplier<String> baseUrl;

    public TossOutboundGuard(Supplier<String> baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** @param path 기본 주소 뒤의 경로(쿼리 제외) */
    public static void check(HttpMethod method, String path) {
        if (path == null || path.contains("%") || path.contains("..") || path.contains("//") || path.contains(";")) {
            throw new BlockedRequestException("허용되지 않은 토스 요청 경로: " + method + " " + path);
        }
        boolean allowed = HttpMethod.POST.equals(method) ? TOKEN_PATH.equals(path)
                : HttpMethod.GET.equals(method) && (GET_PATHS.contains(path) || MARKET_CALENDAR.matcher(path).matches());
        if (!allowed) {
            throw new BlockedRequestException("허용되지 않은 토스 요청: " + method + " " + path);
        }
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        URI base = URI.create(baseUrl.get());
        URI target = request.getURI();
        if (!base.getHost().equalsIgnoreCase(target.getHost()) || base.getPort() != target.getPort()) {
            throw new BlockedRequestException("토스 기본 주소 밖으로 가는 요청: " + request.getMethod() + " " + target.getHost());
        }
        String basePath = base.getRawPath();
        String path = target.getRawPath();
        if (basePath != null && !basePath.isEmpty() && !basePath.equals("/")) {
            String prefix = basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath;
            if (!path.startsWith(prefix + "/")) {
                throw new BlockedRequestException("토스 기본 주소 밖으로 가는 요청: " + request.getMethod());
            }
            path = path.substring(prefix.length());
        }
        check(request.getMethod(), path);
        return execution.execute(request, body);
    }
}
