/**
 * 기능 B(내 계좌 뷰). 원시 데이터(체결·입출금·배당)를 시세에 리플레이해 로그·보유·인사이트를 낸다.
 *
 * <p>계좌는 요청자로 정한다({@link com.uplog.portfolio.PortfolioAccounts}): 토스를 연결하기 전에는 데모 계좌(주인 없는 sample 계좌),
 * 연결하면 자기 ACTIVE 계좌. {@code desktop} 프로필에서 합성 데모 계좌를 시드한다. 실제 수집기는 {@link com.uplog.portfolio.broker.BrokerPort}로 같은 원시 테이블을 채운다.
 */
package com.uplog.portfolio;
