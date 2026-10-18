package com.uplog.localuser;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 맥 사용자 한 명 = 로컬 사용자 한 명({@code app_user}의 가장 작은 id). 첫 실행 때 만들고, 실행 쿠키가 맞는 요청은 모두 이 사용자다.
 * 다른 모듈은 {@link #find(long)}·{@link #exists(long)}로 사용자 정보를 읽는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalUsers implements ApplicationRunner {

    public static final String NICKNAME = "나";
    public static final String PROVIDER = "LOCAL";

    private static final Logger log = LoggerFactory.getLogger(LocalUsers.class);

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private volatile Long cached;

    public LocalUsers(JdbcClient jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** 첫 실행이면 로컬 사용자를 만든다. */
    @Override
    public void run(ApplicationArguments args) {
        log.info("로컬 사용자: {}", currentId());
    }

    /** 로컬 사용자 id. 없으면 만든다. */
    public long currentId() {
        Long id = cached;
        if (id == null) {
            id = ensure();
            cached = id;
        }
        return id;
    }

    public Optional<LocalUser> find(long userId) {
        return jdbc.sql("SELECT id, nickname FROM app_user WHERE id = :id")
                .param("id", userId)
                .query((rs, n) -> new LocalUser(rs.getLong("id"), rs.getString("nickname"), PROVIDER))
                .optional();
    }

    public boolean exists(long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM app_user WHERE id = :id").param("id", userId).query(Long.class).single() > 0;
    }

    private synchronized long ensure() {
        Long id = tx.execute(status -> {
            Optional<Long> existing = first();
            if (existing.isPresent()) {
                return existing.get();
            }
            jdbc.sql("INSERT INTO app_user (nickname) VALUES (:nickname)").param("nickname", NICKNAME).update();
            return first().orElseThrow();
        });
        return id;
    }

    private Optional<Long> first() {
        return jdbc.sql("SELECT MIN(id) FROM app_user").query((rs, n) -> {
            long v = rs.getLong(1);
            return rs.wasNull() ? Optional.<Long>empty() : Optional.of(v);
        }).single();
    }

    /** @param provider 늘 {@code LOCAL} */
    public record LocalUser(long id, String nickname, String provider) {}
}
