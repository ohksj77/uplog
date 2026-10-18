package com.uplog.calc.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 매도 한 건과 그때의 실현손익.
 *
 * @param day 계좌 거래일(체결일이 휴장일이면 다음 거래일)
 * @param avgCostLocal 매도 직전 평균단가(현지) / avgFxRate 매도 직전 평균 매수 환율
 * @param realizedProfit 매도 금액 − 매도분 원화 원가 − 그 체결의 수수료·세금(원)
 */
public record RealizedSale(
        int sequence, Execution execution, LocalDate day, BigDecimal avgCostLocal, BigDecimal avgFxRate, BigDecimal realizedProfit) {}
