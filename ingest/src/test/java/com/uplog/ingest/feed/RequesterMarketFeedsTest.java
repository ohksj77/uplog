package com.uplog.ingest.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uplog.marketdata.feed.MarketFeedException;
import com.uplog.portfolio.PortfolioRepository;
import com.uplog.portfolio.broker.BrokerPort;
import com.uplog.portfolio.connect.BrokerCredentials;
import com.uplog.portfolio.connect.BrokerException;
import com.uplog.portfolio.connect.CredentialReader;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class RequesterMarketFeedsTest {

    private JdbcTemplate jdbc;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private volatile BrokerException.Reason failure;
    private final List<Long> credentialReads = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "sa", "");
        Flyway.configure().dataSource(ds).outOfOrder(true).load().migrate();
        jdbc = new JdbcTemplate(ds);
        jdbc.update("INSERT INTO app_user (nickname, created_at) VALUES ('a', CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO app_user (nickname, created_at) VALUES ('b', CURRENT_TIMESTAMP)");
    }

    private long account(long userId, String status) {
        jdbc.update("""
                INSERT INTO account (name, broker, sample, connected_at, user_id, broker_account_seq, status)
                VALUES ('테스트', 'TOSS', FALSE, ?, ?, '1', ?)""", java.sql.Date.valueOf(LocalDate.of(2026, 10, 1)), userId, status);
        return jdbc.queryForObject("SELECT MAX(id) FROM account", Long.class);
    }

    private BrokerPort broker() {
        return (BrokerPort) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {BrokerPort.class},
                (proxy, method, args) -> {
                    BrokerCredentials key = (BrokerCredentials) args[0];
                    calls.add(method.getName() + ":" + key.clientId());
                    if (failure != null) {
                        throw new BrokerException(failure, "stub");
                    }
                    return method.getReturnType() == java.util.Optional.class ? java.util.Optional.empty() : List.of();
                });
    }

    private RequesterMarketFeeds feeds(Instant now) {
        CredentialReader reader = accountId -> {
            credentialReads.add(accountId);
            if (accountId == 999) {
                throw new CredentialReader.CredentialNotFoundException(accountId);
            }
            return new BrokerCredentials("client-of-" + accountId, "secret");
        };
        return new RequesterMarketFeeds(new PortfolioRepository(jdbc), () -> reader, broker(), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void 요청자_자신의_ACTIVE_계좌_키만_쓰고_없으면_empty다() {
        long a = account(1, "ACTIVE");
        account(2, "DISCONNECTED");
        Instant now = Instant.parse("2026-10-16T01:00:00Z");
        RequesterMarketFeeds feeds = feeds(now);

        assertThat(feeds.forUser(2)).isEmpty();
        assertThat(feeds.forUser(3)).isEmpty();
        assertThat(credentialReads).isEmpty();

        feeds.forUser(1).orElseThrow().dailyCandles("069500", null);
        assertThat(credentialReads).containsOnly(a);
        assertThat(calls).containsExactly("stocks:client-of-" + a, "dailyCandles:client-of-" + a);
    }

    @Test
    void 키_확인은_10분마다_한_번이고_실패는_사유별_MarketFeedException이다() {
        account(1, "ACTIVE");
        Instant now = Instant.parse("2026-10-16T01:00:00Z");
        RequesterMarketFeeds feeds = feeds(now);
        feeds.forUser(1);
        feeds.forUser(1);
        assertThat(calls).hasSize(1);

        failure = BrokerException.Reason.IP_NOT_ALLOWED;
        RequesterMarketFeeds later = feeds(now.plusSeconds(601));
        assertThatThrownBy(() -> later.forUser(1))
                .isInstanceOfSatisfying(MarketFeedException.class,
                        e -> assertThat(e.reason()).isEqualTo(MarketFeedException.Reason.IP_NOT_ALLOWED))
                .hasMessageContaining("IP");
        assertThat(BrokerMarketFeed.reason(BrokerException.Reason.TOKEN_REVOKED))
                .isEqualTo(MarketFeedException.Reason.KEY_IN_USE_ELSEWHERE);
        assertThat(BrokerMarketFeed.reason(BrokerException.Reason.INVALID_CREDENTIALS))
                .isEqualTo(MarketFeedException.Reason.INVALID_KEY);
    }

    @Test
    void 계좌는_있는데_키가_없으면_NO_KEY다() {
        jdbc.update("""
                INSERT INTO account (id, name, broker, sample, connected_at, user_id, broker_account_seq, status)
                VALUES (999, '테스트', 'TOSS', FALSE, DATE '2026-10-01', 1, '1', 'ACTIVE')""");
        assertThatThrownBy(() -> feeds(Instant.now()).forUser(1))
                .isInstanceOfSatisfying(MarketFeedException.class,
                        e -> assertThat(e.reason()).isEqualTo(MarketFeedException.Reason.NO_KEY));
    }
}
