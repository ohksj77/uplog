package com.uplog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import com.uplog.portfolio.publicip.IpifyLookup;
import com.uplog.portfolio.publicip.PublicIpLookup;
import com.uplog.portfolio.publicip.PublicIpService;
import com.uplog.portfolio.publicip.PublicIpUnavailableException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** ipify 클라이언트(가짜 HTTP 서버)와 1분 기억. */
class PublicIpLookupTest {

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>("{\"ip\":\"198.51.100.23\"}");
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger delayMs = new AtomicInteger();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                Thread.sleep(delayMs.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            assertThat(exchange.getRequestURI().getQuery()).isEqualTo("format=json");
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private IpifyLookup lookup() {
        return IpifyLookup.create(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/?format=json"),
                Duration.ofMillis(500));
    }

    @Test
    void ipify_JSON에서_IP를_꺼낸다() {
        assertThat(lookup().lookup()).isEqualTo("198.51.100.23");
        body.set("{\"ip\":\"2001:db8::1\"}");
        assertThat(lookup().lookup()).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void 이상한_응답_오류_시간_초과는_확인_실패다() {
        body.set("{\"ip\":\"evil.example\"}");
        assertThatThrownBy(() -> lookup().lookup()).isInstanceOf(PublicIpUnavailableException.class);
        body.set("<html>");
        assertThatThrownBy(() -> lookup().lookup()).isInstanceOf(PublicIpUnavailableException.class);
        body.set("{\"ip\":\"1.2.3.4\"}");
        status.set(503);
        assertThatThrownBy(() -> lookup().lookup()).isInstanceOf(PublicIpUnavailableException.class);
        status.set(200);
        delayMs.set(1500);
        assertThatThrownBy(() -> lookup().lookup()).isInstanceOf(PublicIpUnavailableException.class);
    }

    @Test
    void 결과는_최대_1분만_기억한다() {
        AtomicInteger calls = new AtomicInteger();
        PublicIpLookup counting = () -> "10.0.0." + calls.incrementAndGet();
        MutableClock clock = new MutableClock();
        PublicIpService service = new PublicIpService(counting, clock, Duration.ofHours(1)); // 1분으로 줄인다

        assertThat(service.current()).isEqualTo("10.0.0.1");
        clock.now = clock.now.plusSeconds(59);
        assertThat(service.current()).isEqualTo("10.0.0.1");
        clock.now = clock.now.plusSeconds(2);
        assertThat(service.current()).isEqualTo("10.0.0.2");

        PublicIpService failing = new PublicIpService(() -> {
            throw new PublicIpUnavailableException("down");
        }, clock, Duration.ofMinutes(1));
        assertThatThrownBy(failing::current).isInstanceOf(PublicIpUnavailableException.class);
    }

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-18T00:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
