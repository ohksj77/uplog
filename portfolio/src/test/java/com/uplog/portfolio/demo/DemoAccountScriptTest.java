package com.uplog.portfolio.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.uplog.calc.PriceSeries;
import com.uplog.calc.Trade;
import com.uplog.calc.portfolio.Execution;
import com.uplog.calc.portfolio.HoldingPeriods;
import com.uplog.calc.portfolio.PortfolioDay;
import com.uplog.calc.portfolio.PortfolioHistory;
import com.uplog.calc.portfolio.PortfolioReplay;
import com.uplog.core.CurrencyCode;
import com.uplog.core.WeekdayTradingCalendar;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class DemoAccountScriptTest {

    private static final LocalDate END = LocalDate.of(2026, 10, 16);
    private static final Map<String, Double> START = Map.of(
            "S00001", 62_000.0, "S00002", 85_000.0, "S00003", 33_000.0, "S00004", 310_000.0,
            "SMPT", 150.0, "SMPC", 90.0, "SMPS", 55.0, "SMPE", 70.0);

    private final Map<String, PriceSeries> closes = new TreeMap<>();
    private final PriceSeries usdKrw;

    DemoAccountScriptTest() {
        List<LocalDate> days = WeekdayTradingCalendar.weekdaysOnly().tradingDays(END.minusYears(3), END);
        START.forEach((code, start) -> closes.put(code, wave(days, start, code.startsWith("S0") ? 0 : 2, code.hashCode())));
        usdKrw = wave(days, 1_350, 2, 7);
    }

    /** 결정적인 출렁임: 시작가 × (1 + 0.15 sin). */
    private static PriceSeries wave(List<LocalDate> days, double start, int scale, int phase) {
        Map<LocalDate, BigDecimal> map = new TreeMap<>();
        for (int i = 0; i < days.size(); i++) {
            double v = start * (1 + 0.15 * Math.sin(i / 23.0 + phase) + i * 0.0002);
            map.put(days.get(i), BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP));
        }
        return new PriceSeries(map);
    }

    private PortfolioHistory replay(DemoAccountScript.Plan plan) {
        Map<String, CurrencyCode> currencies = new TreeMap<>();
        closes.keySet().forEach(c -> currencies.put(c, c.startsWith("S0") ? CurrencyCode.KRW : CurrencyCode.USD));
        List<LocalDate> days = List.copyOf(closes.get("S00001").tradingDays(plan.flows().getFirst().date(), END));
        return PortfolioReplay.replay(new PortfolioReplay.Input(
                days, currencies, closes, usdKrw, plan.trades(), plan.flows(), plan.dividends()));
    }

    @Test
    void 같은_시세면_같은_계좌이고_약_2년치다() {
        DemoAccountScript.Plan plan = DemoAccountScript.generate(closes, usdKrw, END);

        assertThat(DemoAccountScript.generate(closes, usdKrw, END)).isEqualTo(plan);
        assertThat(plan.flows().getFirst().date()).isEqualTo(LocalDate.of(2024, 10, 16));
        assertThat(plan.flows().getFirst().amount()).isEqualByComparingTo("10000000");
        assertThat(plan.trades()).hasSizeGreaterThan(40);
        assertThat(plan.dividends()).isNotEmpty();
        assertThat(plan.connectedAt()).isEqualTo(LocalDate.of(2026, 7, 16));
    }

    @Test
    void 가진_현금_안에서만_사고_가진_수량_안에서만_판다() {
        PortfolioHistory history = replay(DemoAccountScript.generate(closes, usdKrw, END));

        assertThat(history.days()).allSatisfy(d -> assertThat(d.cash().signum()).isNotNegative());
        assertThat(history.first().executions()).isEmpty(); // 첫날은 입금만: 전체 기간 폭포수가 전체 손익과 맞는다
    }

    @Test
    void 단타_스윙_중기_장투_포지션과_보유_중인_포지션이_모두_있다() {
        DemoAccountScript.Plan plan = DemoAccountScript.generate(closes, usdKrw, END);
        List<HoldingPeriods.Period> periods = HoldingPeriods.of(replay(plan));

        Set<HoldingPeriods.Bucket> closed = new HashSet<>();
        periods.stream().filter(p -> !p.isOpen()).forEach(p -> closed.add(p.bucket()));
        assertThat(closed).containsExactlyInAnyOrder(HoldingPeriods.Bucket.values());
        assertThat(periods).filteredOn(HoldingPeriods.Period::isOpen).hasSizeGreaterThanOrEqualTo(3);
        assertThat(periods.stream().map(HoldingPeriods.Period::instrument).distinct()).hasSize(8);

        // 일부 매도(매도 뒤에도 남은 수량이 있음)
        PortfolioHistory history = replay(plan);
        assertThat(history.days().stream().flatMap(d -> d.instruments().values().stream())
                .filter(i -> i.sellQuantity().signum() > 0 && i.position().isOpen())).isNotEmpty();
    }

    @Test
    void 수수료와_세금은_국내_미국_규칙대로다() {
        List<Execution> trades = DemoAccountScript.generate(closes, usdKrw, END).trades();

        assertThat(trades).allSatisfy(t -> {
            if (t.instrument().startsWith("S0")) {
                assertThat(t.fxRate()).isEqualByComparingTo("1");
                assertThat(t.feeLocal()).isEqualByComparingTo(t.grossLocal().multiply(new BigDecimal("0.00015")).setScale(0, RoundingMode.DOWN));
                BigDecimal tax = t.side() == Trade.Side.SELL
                        ? t.grossLocal().multiply(new BigDecimal("0.0018")).setScale(0, RoundingMode.DOWN)
                        : BigDecimal.ZERO;
                assertThat(t.taxLocal()).isEqualByComparingTo(tax);
            } else {
                assertThat(t.fxRate()).isEqualByComparingTo(usdKrw.closeOn(t.date()));
                assertThat(t.feeLocal()).isEqualByComparingTo(t.grossLocal().multiply(new BigDecimal("0.0025")).setScale(2, RoundingMode.HALF_UP));
                assertThat(t.taxLocal()).isZero();
            }
        });
    }

    @Test
    void 최근_평일_이틀은_아무_일도_없는_수집_실패일이다() {
        DemoAccountScript.Plan plan = DemoAccountScript.generate(closes, usdKrw, END);

        assertThat(plan.gaps()).hasSize(2).allSatisfy(gap -> {
            assertThat(gap).isAfter(plan.connectedAt()).isBefore(END);
            assertThat(plan.trades()).noneMatch(t -> t.date().equals(gap));
            assertThat(plan.flows()).noneMatch(f -> f.date().equals(gap));
            assertThat(plan.dividends()).noneMatch(d -> d.date().equals(gap));
        });
        PortfolioDay last = replay(plan).last();
        assertThat(last.date()).isEqualTo(END);
    }
}
