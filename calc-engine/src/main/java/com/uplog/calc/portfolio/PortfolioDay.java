package com.uplog.calc.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 계좌의 하루(장 마감 기준).
 *
 * @param value 총평가금(원) = 현금 + 종목 평가금
 * @param netCashFlow 그날 순입금(입금 +, 출금 −). 배당은 들어가지 않는다.
 * @param pnl value − netCashFlow − 전날 value
 * @param dailyReturn pnl / 전날 value(전날 0이면 0)
 * @param twrIndex 첫날 1.0에서 시작한 누적 시간가중 지수
 * @param netDeposits 그날까지의 순입금 누계
 * @param instruments 그날 보유했거나 거래·배당이 있었던 종목, 코드순
 */
public record PortfolioDay(
        LocalDate date,
        BigDecimal cash,
        BigDecimal value,
        BigDecimal netCashFlow,
        BigDecimal pnl,
        double dailyReturn,
        double twrIndex,
        BigDecimal netDeposits,
        Map<String, InstrumentDay> instruments,
        BigDecimal dividends,
        BigDecimal fees,
        BigDecimal taxes,
        List<Execution> executions,
        List<AccountFlow> flows,
        List<Dividend> dividendEvents) {

    public PortfolioDay {
        instruments = Collections.unmodifiableMap(new LinkedHashMap<>(instruments));
        executions = List.copyOf(executions);
        flows = List.copyOf(flows);
        dividendEvents = List.copyOf(dividendEvents);
    }

    /** 평가손익(원) = 평가금 − 순입금 누계. */
    public BigDecimal profit() {
        return value.subtract(netDeposits);
    }
}
