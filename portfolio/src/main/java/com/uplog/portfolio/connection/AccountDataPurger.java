package com.uplog.portfolio.connection;

/**
 * 연결 해제에서 "기록도 삭제"를 고르면, 계좌 행을 지우기 전에 각 모듈이 자기 테이블의 그 계좌 기록을 지운다
 * (예: ingest의 ingest_job·holding_snapshot·cash_snapshot·sync_cursor). portfolio 테이블은 연결 서비스가 직접 지운다.
 * 같은 트랜잭션 안에서 불린다.
 */
public interface AccountDataPurger {

    void purge(long accountId);
}
