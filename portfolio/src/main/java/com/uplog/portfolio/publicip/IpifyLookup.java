package com.uplog.portfolio.publicip;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * api.ipify.org로 공인 IP를 확인한다({@code GET https://api.ipify.org?format=json} → {@code {"ip":"1.2.3.4"}}).
 * 짧은 시간 제한을 두고, 응답이 IP 문자열이 아니면 받지 않는다. 사용자가 "내 공인 IP 확인"을 누를 때만 불린다.
 */
public final class IpifyLookup implements PublicIpLookup {

    private static final Pattern IP = Pattern.compile("\"ip\"\\s*:\\s*\"([0-9A-Fa-f:.]{2,45})\"");

    private final HttpClient client;
    private final URI uri;
    private final Duration timeout;

    public IpifyLookup(HttpClient client, URI uri, Duration timeout) {
        this.client = client;
        this.uri = uri;
        this.timeout = timeout;
    }

    public static IpifyLookup create(URI uri, Duration timeout) {
        return new IpifyLookup(HttpClient.newBuilder().connectTimeout(timeout).build(), uri, timeout);
    }

    @Override
    public String lookup() {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", "application/json").GET().build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new PublicIpUnavailableException("공인 IP 확인 요청 실패: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PublicIpUnavailableException("공인 IP 확인이 중단됐습니다", e);
        }
        if (response.statusCode() != 200) {
            throw new PublicIpUnavailableException("공인 IP 확인 응답 " + response.statusCode());
        }
        return parse(response.body());
    }

    /** {@code {"ip":"…"}}에서 IP를 꺼낸다. IPv4·IPv6 문자열만 받는다(호스트 이름은 풀지 않는다). */
    static String parse(String body) {
        Matcher m = IP.matcher(body == null ? "" : body);
        if (!m.find()) {
            throw new PublicIpUnavailableException("공인 IP 확인 응답 형식이 다릅니다");
        }
        try {
            return InetAddress.ofLiteral(m.group(1)).getHostAddress();
        } catch (IllegalArgumentException e) {
            throw new PublicIpUnavailableException("공인 IP 확인 응답이 IP가 아닙니다", e);
        }
    }
}
