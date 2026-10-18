package com.uplog.ingest;

import com.uplog.portfolio.connection.AccountDataPurger;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 연결 해제 + 기록 삭제 때 ingest 테이블의 그 계좌 행을 지운다. 연결 서비스의 트랜잭션 안에서 불린다. */
public class IngestDataPurger implements AccountDataPurger {

    private static final String[] TABLES = {"ingest_job", "holding_snapshot", "cash_snapshot", "sync_cursor"};

    private final JdbcClient jdbc;

    public IngestDataPurger(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void purge(long accountId) {
        for (String table : TABLES) {
            jdbc.sql("DELETE FROM " + table + " WHERE account_id = :id").param("id", accountId).update();
        }
    }
}
