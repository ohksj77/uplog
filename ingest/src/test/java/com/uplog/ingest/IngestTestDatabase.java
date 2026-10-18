package com.uplog.ingest;

import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/** 테스트마다 새 H2(PostgreSQL 모드)에 운영과 같은 마이그레이션(market-data, portfolio, ingest)을 적용한다. */
final class IngestTestDatabase {

    final JdbcTemplate jdbcTemplate;
    final JdbcClient jdbc;
    final TransactionTemplate tx;

    IngestTestDatabase() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
                "sa", "");
        Flyway.configure().dataSource(ds).outOfOrder(true).load().migrate();
        jdbcTemplate = new JdbcTemplate(ds);
        jdbc = JdbcClient.create(jdbcTemplate);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }

    long insertAccount(String accountSeq) {
        jdbc.sql("""
                        INSERT INTO account (name, broker, sample, connected_at, user_id, broker_account_seq, status)
                        VALUES ('테스트', 'TOSS', FALSE, :d, 1, :seq, 'ACTIVE')
                        """)
                .param("d", java.sql.Date.valueOf(LocalDate.of(2026, 10, 1)))
                .param("seq", accountSeq)
                .update();
        return jdbc.sql("SELECT MAX(id) FROM account").query(Long.class).single();
    }
}
