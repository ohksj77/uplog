package com.uplog.calc.portfolio;

import static com.uplog.calc.portfolio.Scenario.buy;
import static com.uplog.calc.portfolio.Scenario.entry;
import static com.uplog.calc.portfolio.Scenario.missing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TradingCostsTest {

    @Test
    void 비용은_체결_수수료와_세금이고_그해_손익에_더하면_비용_전_손익이다() {
        // KR1 매수 수수료 1, US1 매수 수수료 0.1달러 × 1,000, KR1 매도 수수료 1 + 세금 2
        TradingCosts.Result r = TradingCosts.of(AccountLog.of(Scenario.history(), Set.of()), 2026);

        assertThat(r.total()).isEqualTo(new TradingCosts.Cost(102, 2));
        assertThat(r.total().cost()).isEqualTo(104);
        assertThat(r.allTime()).isEqualTo(r.total());
        assertThat(r.pnl()).isEqualTo(16_186);
        assertThat(r.profitBeforeCost()).isEqualTo(16_290);
        assertThat(r.costShare()).hasValueSatisfying(s -> assertThat(s).isCloseTo(104.0 / 16_290, within(1e-12)));
        assertThat(r.months()).containsExactly(new TradingCosts.Month(YearMonth.of(2026, 10), new TradingCosts.Cost(102, 2)));
        assertThat(r.byInstrument()).containsExactly(
                new TradingCosts.ByInstrument("US1", new TradingCosts.Cost(100, 0), 1),
                new TradingCosts.ByInstrument("KR1", new TradingCosts.Cost(2, 2), 2));
    }

    @Test
    void 기록이_없는_해는_0이고_비용_전_손익이_0_이하면_비중은_없다() {
        TradingCosts.Result r = TradingCosts.of(AccountLog.of(Scenario.history(), Set.of()), 2025);
        assertThat(r.total()).isEqualTo(TradingCosts.Cost.ZERO);
        assertThat(r.allTime().cost()).isEqualTo(104);
        assertThat(r.months()).isEmpty();
        assertThat(r.byInstrument()).isEmpty();
        assertThat(r.profitBeforeCost()).isZero();
        assertThat(r.costShare()).isEmpty();
    }

    @Test
    void 수집_실패일의_체결은_다음_기록일의_달과_해에_센다() {
        Execution lastYear = buy(LocalDate.of(2025, 12, 31), "KR1", 1, "100", "1", "10", "0");
        List<AccountLog.Entry> log = List.of(
                entry(LocalDate.of(2025, 12, 30), 1_000, 1_000, 0, 0.0),
                missing(LocalDate.of(2025, 12, 31)),
                entry(LocalDate.of(2026, 1, 2), 490, 0, -510, -0.51, lastYear));

        TradingCosts.Result r2026 = TradingCosts.of(log, 2026);
        assertThat(r2026.total()).isEqualTo(new TradingCosts.Cost(10, 0));
        assertThat(r2026.months()).extracting(TradingCosts.Month::month).containsExactly(YearMonth.of(2026, 1));
        assertThat(r2026.profitBeforeCost()).isEqualTo(-500);
        assertThat(r2026.costShare()).isEmpty();

        TradingCosts.Result r2025 = TradingCosts.of(log, 2025);
        assertThat(r2025.total()).isEqualTo(TradingCosts.Cost.ZERO);
        // 2025년 기록일이 있는 달은 12월 하나(비용 0)
        assertThat(r2025.months()).containsExactly(new TradingCosts.Month(YearMonth.of(2025, 12), TradingCosts.Cost.ZERO));
    }
}
