/**
 * 계좌 연결의 모듈 사이 계약(docs/design/account-connection.md).
 *
 * <p>portfolio(연결 API)가 쓰고, broker-toss가 {@link com.uplog.portfolio.connect.BrokerVerifier}를, ingest가
 * {@link com.uplog.portfolio.connect.IngestScheduler}를, credential이 {@link com.uplog.portfolio.connect.CredentialReader}를
 * 구현한다.
 */
package com.uplog.portfolio.connect;
