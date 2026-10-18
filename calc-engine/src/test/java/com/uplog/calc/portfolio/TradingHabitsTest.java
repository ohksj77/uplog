package com.uplog.calc.portfolio;

import static com.uplog.calc.portfolio.Scenario.buy;
import static com.uplog.calc.portfolio.Scenario.deposit;
import static com.uplog.calc.portfolio.Scenario.entry;
import static com.uplog.calc.portfolio.Scenario.missing;
import static com.uplog.calc.portfolio.Scenario.sell;
import static com.uplog.calc.portfolio.Scenario.series;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.uplog.core.CurrencyCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TradingHabitsTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    /** days일 보유하고 수익률 returnPct%로 끝난(closed) 포지션. 매수 100원 기준. */
    private static HoldingPeriods.Period closed(long days, long returnPct) {
        return new HoldingPeriods.Period("KR1", START, Optional.of(START.plusDays(days)), days, BigDecimal.valueOf(100),
                BigDecimal.valueOf(100 + returnPct), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static HoldingPeriods.Period open(long days, long returnPct) {
        return new HoldingPeriods.Period("KR1", START, Optional.empty(), days, BigDecimal.valueOf(100), BigDecimal.ZERO,
                BigDecimal.valueOf(100 + returnPct), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void 끝난_포지션만_이익과_손실로_나눠_보유_일수를_비교한다() {
        TradingHabits.Holding h = TradingHabits.holding(List.of(
                closed(10, 5), closed(20, 15), closed(40, -10), closed(5, 0), open(300, -50)));

        assertThat(h.winners().count()).isEqualTo(2);
        assertThat(h.winners().avgDays()).isEqualTo(15.0);
        assertThat(h.winners().medianDays()).isEqualTo(15.0);
        assertThat(h.winners().avgReturn()).isCloseTo(0.1, within(1e-12));
        // 수익률 0은 손실 쪽
        assertThat(h.losers().count()).isEqualTo(2);
        assertThat(h.losers().avgDays()).isEqualTo(22.5);
        assertThat(h.losers().medianDays()).isEqualTo(22.5);
        assertThat(h.losers().avgReturn()).isCloseTo(-0.05, within(1e-12));
        assertThat(h.ratio()).hasValueSatisfying(r -> assertThat(r).isCloseTo(1.5, within(1e-12)));
        assertThat(h.verdict()).isEqualTo(TradingHabits.Verdict.HOLD_LOSERS); // 정확히 1.5배면 HOLD_LOSERS
    }

    @Test
    void 판정_경계는_1점5배와_그_역수이고_한쪽이_2개_미만이면_판정하지_않는다() {
        // 손실 평균 14.5일 / 이익 10일 = 1.45 → BALANCED
        assertThat(TradingHabits.holding(List.of(closed(10, 1), closed(10, 1), closed(14, -1), closed(15, -1))).verdict())
                .isEqualTo(TradingHabits.Verdict.BALANCED);
        // 손실 10일 / 이익 15일 = 1/1.5 정확히 → CUT_LOSSES
        assertThat(TradingHabits.holding(List.of(closed(15, 1), closed(15, 1), closed(10, -1), closed(10, -1))).verdict())
                .isEqualTo(TradingHabits.Verdict.CUT_LOSSES);
        // 손실 11일 / 이익 15일 = 0.733… → BALANCED
        assertThat(TradingHabits.holding(List.of(closed(15, 1), closed(15, 1), closed(11, -1), closed(11, -1))).verdict())
                .isEqualTo(TradingHabits.Verdict.BALANCED);
        // 손실 1개
        TradingHabits.Holding oneLoser = TradingHabits.holding(List.of(closed(1, 1), closed(1, 1), closed(100, -1)));
        assertThat(oneLoser.verdict()).isEqualTo(TradingHabits.Verdict.INSUFFICIENT);
        assertThat(oneLoser.ratio()).hasValue(100.0);
        // 이익 0개 → ratio 없음
        TradingHabits.Holding noWinner = TradingHabits.holding(List.of(closed(3, -1), closed(5, -2)));
        assertThat(noWinner.ratio()).isEmpty();
        assertThat(noWinner.winners()).isEqualTo(new TradingHabits.Side(0, 0.0, 0.0, 0.0));
        assertThat(noWinner.verdict()).isEqualTo(TradingHabits.Verdict.INSUFFICIENT);
        // 이익 포지션이 모두 당일 청산(0일)이면 ratio는 없지만 손실을 더 오래 든 것이다
        TradingHabits.Holding sameDay = TradingHabits.holding(List.of(closed(0, 1), closed(0, 1), closed(3, -1), closed(5, -1)));
        assertThat(sameDay.ratio()).isEmpty();
        assertThat(sameDay.verdict()).isEqualTo(TradingHabits.Verdict.HOLD_LOSERS);
    }

    /**
     * KR1 종가 월 100, 화 90, 수 120, 목 110, 금 70.
     * 월 10주 @100, 화 10주 @90(물타기: 평균 100), 수 5주 @120, 목 25주 @110 전량 매도,
     * 금 10주 @80(새 포지션), 같은 날 10주 @70(물타기: 평균 80).
     */
    private static PortfolioHistory averagingHistory() {
        List<LocalDate> days = Scenario.weekdays(5);
        return PortfolioReplay.replay(new PortfolioReplay.Input(
                days,
                Map.of("KR1", CurrencyCode.KRW),
                Map.of("KR1", series("100", "90", "120", "110", "70")),
                null,
                List.of(
                        buy(days.get(0), "KR1", 10, "100", "1", "0", "0"),
                        buy(days.get(1), "KR1", 10, "90", "1", "0", "0"),
                        buy(days.get(2), "KR1", 5, "120", "1", "0", "0"),
                        sell(days.get(3), "KR1", 25, "110", "1", "0", "0"),
                        buy(days.get(4), "KR1", 10, "80", "1", "0", "0"),
                        buy(days.get(4), "KR1", 10, "70", "1", "0", "0")),
                List.of(deposit(days.get(0), 100_000)),
                List.of()));
    }

    @Test
    void 물타기는_보유_중_평균단가보다_낮게_더_산_체결이다() {
        PortfolioHistory history = averagingHistory();
        List<HoldingPeriods.Period> periods = HoldingPeriods.of(history);
        assertThat(periods).hasSize(2);
        assertThat(periods.getFirst().returnRate()).isCloseTo(0.1, within(1e-12)); // 2,750 / 2,500 − 1
        assertThat(periods.getLast().returnRate()).isCloseTo(-0.1 / 1.5, within(1e-12)); // 1,400 / 1,500 − 1

        TradingHabits.AveragingDown a = TradingHabits.averagingDown(history, periods);

        assertThat(a.totalBuys()).isEqualTo(5);
        assertThat(a.items()).hasSize(2);
        assertThat(a.shareOfBuys()).isCloseTo(0.4, within(1e-12));
        assertThat(a.positions()).isEqualTo(2);
        assertThat(a.positionsWon()).isEqualTo(1);

        TradingHabits.AveragingDownBuy latest = a.items().getFirst(); // 최신 순
        assertThat(latest.execution().priceLocal()).isEqualByComparingTo("70");
        assertThat(latest.avgCostLocal()).isEqualByComparingTo("80"); // 같은 날 앞선 매수까지 반영한 평균
        assertThat(latest.belowAvg()).isCloseTo(-0.125, within(1e-12));
        assertThat(latest.periodIndex()).isEqualTo(1);

        TradingHabits.AveragingDownBuy earlier = a.items().getLast();
        assertThat(earlier.day()).isEqualTo(Scenario.TUE);
        assertThat(earlier.avgCostLocal()).isEqualByComparingTo("100");
        assertThat(earlier.belowAvg()).isCloseTo(-0.1, within(1e-12));
        assertThat(earlier.period()).isSameAs(periods.getFirst());
    }

    @Test
    void 물타기가_없으면_비율은_0이다() {
        TradingHabits.AveragingDown a = TradingHabits.averagingDown(Scenario.history(), HoldingPeriods.of(Scenario.history()));
        assertThat(a.totalBuys()).isEqualTo(2);
        assertThat(a.items()).isEmpty();
        assertThat(a.shareOfBuys()).isZero();
        assertThat(a.positions()).isZero();
    }

    @Test
    void 월별_활동은_첫_달부터_마지막_달까지_빠짐없고_회전율은_매도_금액_나누기_평균_평가금이다() {
        Execution buyJan = buy(LocalDate.of(2026, 1, 30), "KR1", 1, "100", "1", "0", "0");
        Execution sellMar = sell(LocalDate.of(2026, 3, 31), "KR1", 5, "100", "1", "0", "0"); // 수집 실패일 체결
        Execution buyApr = buy(LocalDate.of(2026, 4, 1), "KR1", 1, "100", "1", "0", "0");
        Execution sellApr = sell(LocalDate.of(2026, 4, 2), "KR1", 1, "100", "1", "0", "0");
        List<AccountLog.Entry> log = new ArrayList<>(List.of(
                entry(LocalDate.of(2026, 1, 30), 1_000, 1_000, 0, 0.0, buyJan),
                missing(LocalDate.of(2026, 3, 31)),
                entry(LocalDate.of(2026, 4, 1), 3_000, 0, 0, 0.0, sellMar, buyApr), // AccountLog가 몰아 넣은 대로
                entry(LocalDate.of(2026, 4, 2), 1_000, 0, 0, 0.0, sellApr)));

        List<TradingHabits.Activity> activity = TradingHabits.activity(log);

        assertThat(activity).extracting(TradingHabits.Activity::month).containsExactly(
                YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3), YearMonth.of(2026, 4));
        assertThat(activity.get(0)).isEqualTo(new TradingHabits.Activity(YearMonth.of(2026, 1), 1, 0, 0.0));
        assertThat(activity.get(1)).isEqualTo(new TradingHabits.Activity(YearMonth.of(2026, 2), 0, 0, 0.0));
        assertThat(activity.get(2)).isEqualTo(new TradingHabits.Activity(YearMonth.of(2026, 3), 0, 0, 0.0));
        TradingHabits.Activity apr = activity.get(3);
        assertThat(apr.buys()).isEqualTo(1);
        assertThat(apr.sells()).isEqualTo(2);
        assertThat(apr.turnover()).isCloseTo(600.0 / 2_000, within(1e-12));
    }
}
