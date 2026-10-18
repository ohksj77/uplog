package com.uplog.calc.portfolio;

import static com.uplog.calc.portfolio.Scenario.buy;
import static com.uplog.calc.portfolio.Scenario.d;
import static com.uplog.calc.portfolio.Scenario.deposit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.uplog.calc.PriceSeries;
import com.uplog.core.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DividendIncomeTest {

    private static final LocalDate D1 = LocalDate.of(2025, 9, 30);
    private static final LocalDate D2 = LocalDate.of(2025, 12, 1);
    private static final LocalDate D3 = LocalDate.of(2026, 10, 15);

    /** 거래일 세 개(2025-09-30, 2025-12-01, 2026-10-15)에 걸친 두 종목과 배당 다섯 건(하나는 마지막 날 뒤). */
    private static PortfolioHistory history() {
        PriceSeries flat = new PriceSeries(Map.of(D1, d("100"), D2, d("100"), D3, d("100")));
        return PortfolioReplay.replay(new PortfolioReplay.Input(
                List.of(D1, D2, D3),
                Map.of("KR1", CurrencyCode.KRW, "KR2", CurrencyCode.KRW),
                Map.of("KR1", flat, "KR2", flat),
                null,
                List.of(buy(D1, "KR1", 10, "100", "1", "0", "0"), buy(D1, "KR2", 10, "100", "1", "0", "0")),
                List.of(deposit(D1, 10_000)),
                List.of(
                        new Dividend(D1, "KR1", d("50.4")),
                        new Dividend(LocalDate.of(2025, 11, 29), "KR2", d("30")), // 토요일 → 12-01에 들어오지만 11월로 센다
                        new Dividend(D2, "KR1", d("100")),
                        new Dividend(D3, "KR2", d("200")),
                        new Dividend(D3.plusDays(1), "KR1", d("999")))));
    }

    @Test
    void 배당은_지급일의_달로_묶고_첫_달부터_마지막_달까지_빠짐없이_낸다() {
        DividendIncome.Result r = DividendIncome.of(history(), BigDecimal.valueOf(33_000));

        assertThat(r.months()).hasSize(14);
        assertThat(r.months().getFirst().month()).isEqualTo(YearMonth.of(2025, 9));
        assertThat(r.months().getLast().month()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(r.months().get(0).amount()).isEqualTo(50); // 50.4 → 50
        assertThat(r.months().get(1).items()).isEmpty();
        assertThat(r.months().get(2).items()).singleElement()
                .isEqualTo(new DividendIncome.Item(LocalDate.of(2025, 11, 29), "KR2", 30));
        assertThat(r.months().get(3).amount()).isEqualTo(100);
        assertThat(r.months().stream().mapToLong(DividendIncome.Month::amount).sum()).isEqualTo(r.total());

        assertThat(r.total()).isEqualTo(380); // 마지막 날 뒤의 999는 빠진다
        assertThat(r.byYear()).containsExactly(new DividendIncome.Year(2025, 180), new DividendIncome.Year(2026, 200));
        assertThat(r.byInstrument()).containsExactly(
                new DividendIncome.ByInstrument("KR2", 230, 2), new DividendIncome.ByInstrument("KR1", 150, 2));
        assertThat(r.trailing12m()).isEqualTo(330); // 2025-10-15 이후: 30 + 100 + 200
        assertThat(r.trailingYield()).isCloseTo(0.01, within(1e-12));
    }

    @Test
    void 배당이_없으면_모든_달이_0이고_수익률도_0이다() {
        PortfolioHistory history = PortfolioReplay.replay(new PortfolioReplay.Input(
                Scenario.DAYS, Scenario.CURRENCIES, Scenario.input().closes(), Scenario.input().usdKrw(),
                Scenario.input().executions(), Scenario.input().flows(), List.of()));
        DividendIncome.Result r = DividendIncome.of(history, BigDecimal.ZERO);
        assertThat(r.months()).singleElement().satisfies(m -> {
            assertThat(m.amount()).isZero();
            assertThat(m.items()).isEmpty();
        });
        assertThat(r.byYear()).containsExactly(new DividendIncome.Year(2026, 0));
        assertThat(r.byInstrument()).isEmpty();
        assertThat(r.trailingYield()).isZero();
    }
}
