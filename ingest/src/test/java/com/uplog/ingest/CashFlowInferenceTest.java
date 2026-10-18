package com.uplog.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.uplog.calc.Trade;
import com.uplog.calc.portfolio.AccountFlow;
import com.uplog.calc.portfolio.Execution;
import com.uplog.core.CurrencyCode;
import com.uplog.ingest.CashFlowInference.TradeCash;
import com.uplog.ingest.IngestStore.CashSnapshot;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CashFlowInferenceTest {

    private static final LocalDate D0 = LocalDate.of(2026, 10, 14);
    private static final LocalDate D1 = LocalDate.of(2026, 10, 15);
    private static final BigDecimal FX = new BigDecimal("1400");

    private static CashSnapshot cash(LocalDate d, long krw, String usd, BigDecimal fx) {
        return new CashSnapshot(d, BigDecimal.valueOf(krw), new BigDecimal(usd), fx);
    }

    private static TradeCash krw(Trade.Side side, long gross, long fee, long tax) {
        return new TradeCash(CurrencyCode.KRW, side, BigDecimal.valueOf(gross), BigDecimal.valueOf(fee), BigDecimal.valueOf(tax));
    }

    @Test
    void 거래가_없는데_현금이_늘면_입금이다() {
        BigDecimal flow = CashFlowInference.inferred(cash(D0, 1_000_000, "0", FX), cash(D1, 1_500_000, "0", FX), List.of());
        assertThat(flow).isEqualByComparingTo("500000");
    }

    @Test
    void 거래가_없는데_현금이_줄면_출금이다() {
        BigDecimal flow = CashFlowInference.inferred(cash(D0, 1_000_000, "0", FX), cash(D1, 700_000, "0", FX), List.of());
        assertThat(flow).isEqualByComparingTo("-300000");
    }

    @Test
    void 매매로_바뀐_현금은_유입이_아니다() {
        // 70만원어치 사고(수수료 105원) 30만원어치 팔았다(수수료 45원, 세금 540원)
        List<TradeCash> trades = List.of(
                krw(Trade.Side.BUY, 700_000, 105, 0),
                krw(Trade.Side.SELL, 300_000, 45, 540));
        long after = 1_000_000 - 700_105 + 300_000 - 585;
        assertThat(CashFlowInference.inferred(cash(D0, 1_000_000, "0", FX), cash(D1, after, "0", FX), trades)).isZero();
        // 같은 날 20만원 입금도 했다
        assertThat(CashFlowInference.inferred(cash(D0, 1_000_000, "0", FX), cash(D1, after + 200_000, "0", FX), trades))
                .isEqualByComparingTo("200000");
    }

    @Test
    void 달러_현금은_달러끼리_빼고_그날_환율로_바꾼다() {
        // 달러 100을 들고만 있었고 환율이 1400 → 1450: 유입 없음
        assertThat(CashFlowInference.inferred(cash(D0, 0, "100", FX), cash(D1, 0, "100", new BigDecimal("1450")), List.of()))
                .isZero();
        // 원화 14만원을 달러 100으로 환전(스프레드 없음): 유입 없음
        assertThat(CashFlowInference.inferred(cash(D0, 140_000, "0", FX), cash(D1, 0, "100.00", FX), List.of())).isZero();
        // 달러 50.5(수수료 0.05 포함)로 주식을 사고 달러 배당 10이 들어왔다: 유입 = 10 × 1400
        TradeCash buy = new TradeCash(CurrencyCode.USD, Trade.Side.BUY, new BigDecimal("50.45"), new BigDecimal("0.05"),
                BigDecimal.ZERO);
        assertThat(CashFlowInference.inferred(cash(D0, 0, "100", FX), cash(D1, 0, "59.50", FX), List.of(buy)))
                .isEqualByComparingTo("14000");
    }

    @Test
    void 첫_스냅샷은_장부_현금과의_차이를_유입으로_본다() {
        List<Execution> executions = List.of(new Execution(D0, "005930", Trade.Side.BUY, BigDecimal.TEN,
                new BigDecimal("70000"), BigDecimal.ONE, new BigDecimal("105"), BigDecimal.ZERO));
        List<AccountFlow> flows = List.of(new AccountFlow(D0, AccountFlow.Type.DEPOSIT, new BigDecimal("700105")));
        // 장부 현금 0, 실제 현금 30만원 + 달러 10
        assertThat(CashFlowInference.reconcileWithLedger(cash(D1, 300_000, "10", FX), executions, flows))
                .isEqualByComparingTo("314000");
    }

    @Test
    void 역산_구간은_현금이_모자란_날마다_모자란_만큼_입금으로_본다() {
        List<Execution> executions = List.of(
                new Execution(D0, "A", Trade.Side.BUY, BigDecimal.TEN, new BigDecimal("1000"), BigDecimal.ONE, BigDecimal.TEN,
                        BigDecimal.ZERO),
                new Execution(D1, "A", Trade.Side.SELL, BigDecimal.ONE.add(BigDecimal.ONE), new BigDecimal("1100"),
                        BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO),
                new Execution(D1.plusDays(1), "B", Trade.Side.BUY, BigDecimal.ONE, new BigDecimal("3000"), BigDecimal.ONE,
                        BigDecimal.ZERO, BigDecimal.ZERO));
        List<AccountFlow> deposits = CashFlowInference.minimalDeposits(executions, List.of());
        assertThat(deposits).extracting(AccountFlow::date).containsExactly(D0, D1.plusDays(1));
        assertThat(deposits.get(0).amount()).isEqualByComparingTo("10010");
        assertThat(deposits.get(1).amount()).isEqualByComparingTo("800"); // 매도로 생긴 2200원을 먼저 쓴다
        List<AccountFlow> all = new java.util.ArrayList<>(deposits);
        assertThat(CashFlowInference.ledgerCash(executions, all)).isZero();
    }
}
