package com.uplog.ingest;

import com.uplog.calc.Trade;
import com.uplog.calc.portfolio.AccountFlow;
import com.uplog.calc.portfolio.Execution;
import com.uplog.core.CurrencyCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 입출금·배당 API가 없어서 현금 변화로 외부 유입을 추정한다(docs/toss-openapi-notes.md "현금흐름 추정").
 *
 * <pre>
 * 외부 유입_t = (현금_t − 현금_{t−1}) − (매도 금액 − 매수 금액 − 수수료 − 세금)_t
 * </pre>
 *
 * <p>현금은 원화·달러 매수 가능 금액이다. 통화별로 따로 빼서 달러 쪽 차이만 그날 환율로 원화로 바꾼다. 그래서 달러 현금을
 * 들고 있기만 한 날의 환율 변동이나 원화↔달러 환전은 유입으로 잡히지 않는다(환전 스프레드만 남는다).
 * 양수는 입금 또는 배당·이자, 음수는 출금으로 본다. 배당과 입금은 구분할 수 없다.
 */
public final class CashFlowInference {

    private CashFlowInference() {}

    /** 체결 하나의 현금 영향 재료. 금액·비용은 거래 통화. */
    public record TradeCash(CurrencyCode currency, Trade.Side side, BigDecimal grossLocal, BigDecimal feeLocal, BigDecimal taxLocal) {

        /** 매도 +(금액 − 비용), 매수 −(금액 + 비용). */
        public BigDecimal netLocal() {
            BigDecimal costs = feeLocal.add(taxLocal);
            return side == Trade.Side.SELL ? grossLocal.subtract(costs) : grossLocal.add(costs).negate();
        }
    }

    /**
     * 직전 스냅샷과 오늘 스냅샷 사이의 외부 유입(원).
     *
     * @param trades 직전 스냅샷 날짜 다음날부터 오늘까지의 체결
     */
    public static BigDecimal inferred(
            IngestStore.CashSnapshot previous, IngestStore.CashSnapshot today, List<TradeCash> trades) {
        BigDecimal tradesKrw = BigDecimal.ZERO;
        BigDecimal tradesUsd = BigDecimal.ZERO;
        for (TradeCash t : trades) {
            if (t.currency() == CurrencyCode.USD) {
                tradesUsd = tradesUsd.add(t.netLocal());
            } else {
                tradesKrw = tradesKrw.add(t.netLocal());
            }
        }
        BigDecimal krw = today.krw().subtract(previous.krw()).subtract(tradesKrw);
        BigDecimal usd = today.usd().subtract(previous.usd()).subtract(tradesUsd);
        return krw.add(usd.multiply(today.fx())).setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * 직전 스냅샷이 없을 때(연결 후 첫 일일 수집): 지금까지 기록(역산한 체결 + 기존 입출금)으로 계산한 원화 장부 현금과
     * 실제 현금의 차이를 그날 유입으로 본다.
     *
     * @param executions 오늘까지의 모든 체결 / flows 오늘 이전의 입출금(오늘 추정분 제외)
     */
    public static BigDecimal reconcileWithLedger(IngestStore.CashSnapshot today, List<Execution> executions, List<AccountFlow> flows) {
        return today.totalKrw().subtract(ledgerCash(executions, flows)).setScale(0, RoundingMode.HALF_UP);
    }

    /** 리플레이와 같은 규칙의 원화 장부 현금: 입출금 + 매도 대금 − 매수 대금 − 비용(체결 환율로 원화 환산). */
    public static BigDecimal ledgerCash(List<Execution> executions, List<AccountFlow> flows) {
        BigDecimal cash = BigDecimal.ZERO;
        for (AccountFlow f : flows) {
            cash = cash.add(f.signedAmount());
        }
        for (Execution e : executions) {
            cash = cash.add(net(e));
        }
        return cash;
    }

    /**
     * 역산 구간에는 현금 기록이 없다. 장부 현금이 음수가 되는 날마다 모자란 만큼 입금이 있었다고 본다(최소 입금 가정).
     * 그래야 평가금이 "보유 종목 + 0 이상의 현금"이 되어 일별 수익률이 의미를 가진다.
     *
     * @param flows 이미 기록된 입출금(추정분 제외)
     */
    public static List<AccountFlow> minimalDeposits(List<Execution> executions, List<AccountFlow> flows) {
        Map<LocalDate, BigDecimal> byDay = new TreeMap<>();
        flows.forEach(f -> byDay.merge(f.date(), f.signedAmount(), BigDecimal::add));
        executions.forEach(e -> byDay.merge(e.date(), net(e), BigDecimal::add));
        List<AccountFlow> deposits = new ArrayList<>();
        BigDecimal cash = BigDecimal.ZERO;
        for (Map.Entry<LocalDate, BigDecimal> day : byDay.entrySet()) {
            cash = cash.add(day.getValue());
            if (cash.signum() < 0) {
                BigDecimal need = cash.negate().setScale(0, RoundingMode.CEILING);
                deposits.add(new AccountFlow(day.getKey(), AccountFlow.Type.DEPOSIT, need));
                cash = cash.add(need);
            }
        }
        return deposits;
    }

    private static BigDecimal net(Execution e) {
        BigDecimal costs = e.feeKrw().add(e.taxKrw());
        return e.side() == Trade.Side.SELL ? e.grossKrw().subtract(costs) : e.grossKrw().add(costs).negate();
    }
}
