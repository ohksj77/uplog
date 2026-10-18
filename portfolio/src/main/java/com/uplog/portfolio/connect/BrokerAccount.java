package com.uplog.portfolio.connect;

/**
 * 증권사 계좌 하나.
 *
 * @param accountSeq 토스 {@code X-Tossinvest-Account} 헤더 값
 * @param maskedAccountNo 화면에 보여 줄 가린 계좌번호
 * @param type 토스 accountType(예: BROKERAGE)
 */
public record BrokerAccount(String accountSeq, String maskedAccountNo, String type) {}
