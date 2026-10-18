package com.uplog.marketdata;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 테스트마다 새 H2(PostgreSQL 모드) 메모리 DB에 운영과 같은 Flyway 마이그레이션을 적용한다. */
public final class TestDatabase {

    private TestDatabase() {}

    public static JdbcTemplate migrated() {
        DataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        Flyway.configure().dataSource(ds).load().migrate();
        return new JdbcTemplate(ds);
    }
}
