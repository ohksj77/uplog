/**
 * {@code /api/me}와 토스 계좌 연결 API. 키 검증은 {@link com.uplog.portfolio.connect.BrokerVerifier}, 수집 예약은
 * {@link com.uplog.portfolio.connect.IngestScheduler}, 키 보관은 {@link com.uplog.portfolio.connection.CredentialStore}(credential 모듈)에
 * 맡긴다. 앞의 둘이 없으면 {@link com.uplog.portfolio.connection.ConnectionFallbackAutoConfiguration}의 대체 빈이 뜬다.
 */
package com.uplog.portfolio.connection;
