package com.uplog.auth;

import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** app_user·user_identity 접근. 다른 모듈은 {@link #find(long)}로 사용자 정보를 읽는다. */
@Component
public class UserRepository {

    public static final String KAKAO = "KAKAO";
    public static final String DEV = "DEV";

    static final int NICKNAME_MAX = 40;

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public UserRepository(JdbcClient jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public Optional<AuthUser> find(long userId) {
        return jdbc.sql("""
                        SELECT u.id, u.nickname,
                               (SELECT i.provider FROM user_identity i WHERE i.user_id = u.id ORDER BY i.id LIMIT 1) AS provider
                        FROM app_user u WHERE u.id = :id
                        """)
                .param("id", userId)
                .query((rs, n) -> new AuthUser(rs.getLong("id"), rs.getString("nickname"), rs.getString("provider")))
                .optional();
    }

    public boolean exists(long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM app_user WHERE id = :id").param("id", userId).query(Long.class).single() > 0;
    }

    /** (provider, subject)의 사용자를 찾고, 없으면 만든다. 이미 있으면 닉네임은 바꾸지 않는다. */
    public AuthUser upsert(String provider, String subject, String nickname) {
        try {
            return tx.execute(s -> findOrCreate(provider, subject, nickname));
        } catch (DuplicateKeyException e) {
            // 같은 계정의 첫 로그인이 동시에 들어와 다른 쪽이 먼저 만들었다
            return tx.execute(s -> findOrCreate(provider, subject, nickname));
        }
    }

    private AuthUser findOrCreate(String provider, String subject, String nickname) {
        Optional<Long> existing = jdbc.sql("SELECT user_id FROM user_identity WHERE provider = :provider AND subject = :subject")
                .param("provider", provider)
                .param("subject", subject)
                .query(Long.class)
                .optional();
        if (existing.isPresent()) {
            return find(existing.get()).orElseThrow();
        }
        String name = nickname.length() > NICKNAME_MAX ? nickname.substring(0, NICKNAME_MAX) : nickname;
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO app_user (nickname) VALUES (:nickname)").param("nickname", name).update(key, "id");
        long userId = key.getKeyAs(Long.class);
        jdbc.sql("INSERT INTO user_identity (user_id, provider, subject) VALUES (:userId, :provider, :subject)")
                .param("userId", userId)
                .param("provider", provider)
                .param("subject", subject)
                .update();
        return new AuthUser(userId, name, provider);
    }
}
