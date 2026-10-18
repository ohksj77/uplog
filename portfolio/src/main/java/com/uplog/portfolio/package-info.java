/**
 * 기능 B(내 계좌 뷰). 원시 데이터(체결·입출금·배당)를 시세에 리플레이해 로그·보유·인사이트를 낸다.
 *
 * <p>로그인이 아직 없어 "현재 계좌"는 하나(가장 먼저 만든 계좌)다. 토스 OpenAPI는 아직 확인 전이라
 * {@code local} 프로필에서만 합성 데모 계좌를 시드한다. 실제 수집기는 {@link com.uplog.portfolio.broker.BrokerPort}로 같은 원시 테이블을 채운다.
 */
package com.uplog.portfolio;
