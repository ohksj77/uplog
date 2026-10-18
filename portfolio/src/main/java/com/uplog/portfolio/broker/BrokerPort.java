package com.uplog.portfolio.broker;

import com.uplog.calc.Trade;
import com.uplog.calc.portfolio.AccountFlow;
import com.uplog.core.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 증권사에서 계좌 원시 데이터를 받아 오는 포트. 수집 잡(batch)이 이걸로 받아 trade·cash_flow·dividend에 쌓고,
 * 가입 전 기간은 같은 데이터를 리플레이해 역산한다.
 *
 * <p>TODO: {@code TossBrokerAdapter}. 토스증권 OpenAPI는 키가 없고 응답 형식·조회 전용 권한·체결 내역 조회 가능 기간이
 * 확인되지 않아 구현하지 않았다(docs/ARCHITECTURE.md 8. 미정 사항). 확인되면 API 그룹별 레이트리밋과 재시도를 붙여 구현한다.
 */
public interface BrokerPort {

    /** @param externalId 증권사 쪽 계좌 식별자 */
    record BrokerAccount(long accountId, String externalId) {}

    /** 가격·수수료·세금은 현지 통화, fxRate는 체결 환율(원화 종목은 1). */
    record BrokerTrade(
            String brokerTradeId,
            LocalDate date,
            String instrumentCode,
            CurrencyCode currency,
            Trade.Side side,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal fxRate,
            BigDecimal fee,
            BigDecimal tax) {}

    /** @param amount 원, 양수 */
    record BrokerCashFlow(String brokerFlowId, LocalDate date, AccountFlow.Type type, BigDecimal amount) {}

    /** @param amount 원, 세후 */
    record BrokerDividend(String brokerDividendId, LocalDate date, String instrumentCode, BigDecimal amount) {}

    /** 증권사가 보여 주는 보유 현황. 리플레이 결과와 맞춰 보는(대사) 데 쓴다. */
    record BrokerPosition(String instrumentCode, BigDecimal quantity, BigDecimal avgCostLocal) {}

    List<BrokerTrade> trades(BrokerAccount account, LocalDate from, LocalDate to);

    List<BrokerCashFlow> cashFlows(BrokerAccount account, LocalDate from, LocalDate to);

    List<BrokerDividend> dividends(BrokerAccount account, LocalDate from, LocalDate to);

    List<BrokerPosition> positions(BrokerAccount account, LocalDate asOf);
}
