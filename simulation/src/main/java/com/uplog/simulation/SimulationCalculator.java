package com.uplog.simulation;

import com.uplog.calc.DailyReturn;
import com.uplog.calc.PainMetrics;
import com.uplog.calc.PerformanceSummary;
import com.uplog.calc.PriceSeries;
import com.uplog.calc.PurchasePlan;
import com.uplog.calc.ReplayPoint;
import com.uplog.calc.Simulation;
import com.uplog.calc.Streaks;
import com.uplog.simulation.api.SimulationResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;

/** 한 ETF에 대해 거치식·적립식을 같은 총 투입금으로 계산해 응답 형태로 바꾼다. 스프링 의존 없음. */
public final class SimulationCalculator {

    private SimulationCalculator() {}

    public record Pair(SimulationResponse.Result lumpSum, SimulationResponse.Result recurring) {}

    public static Pair calculate(SimulationQuery query, PriceSeries prices, LocalDate from, LocalDate to) {
        NavigableSet<LocalDate> tradingDays = prices.tradingDays(from, to);
        int count = query.schedule().purchaseCount(tradingDays);
        if (count == 0) {
            throw new SimulationException(ErrorCode.NO_PRICE_DATA, "기간 안에 매수할 수 있는 거래일이 없습니다: " + from + " ~ " + to);
        }
        PlanAmounts amounts = PlanAmounts.of(query.amountType(), query.amount(), count);
        PurchasePlan lumpSum = new PurchasePlan.LumpSum(amounts.lumpSumAmount());
        PurchasePlan recurring = query.schedule().plan(amounts.perPurchaseAmount());
        return new Pair(
                toResult(Simulation.run(lumpSum, prices, from, to), null),
                toResult(Simulation.run(recurring, prices, from, to), streakUnit(query.schedule().frequency())));
    }

    /** @param purchaseUnit 적립 연속을 셀 주기. 거치식이면 null. */
    static SimulationResponse.Result toResult(Simulation sim, Streaks.Unit purchaseUnit) {
        PerformanceSummary s = sim.summary();
        long finalValue = won(s.finalValue());
        long invested = won(s.invested());
        // profit은 반올림한 두 값의 차로 둬서 화면에서 finalValue − invested와 항상 맞게 한다.
        SimulationResponse.Summary summary = new SimulationResponse.Summary(
                finalValue, invested, finalValue - invested, s.totalReturn(), s.twr(), s.annualizedTwr());

        PainMetrics p = sim.pain();
        SimulationResponse.Pain pain = new SimulationResponse.Pain(
                new SimulationResponse.MaxDrawdown(p.maxDrawdown().rate(), p.maxDrawdown().peak(), p.maxDrawdown().trough()),
                p.longestUnderwater()
                        .map(u -> new SimulationResponse.Underwater(u.start(), u.end(), u.recovered(), u.days()))
                        .orElse(null),
                new SimulationResponse.WorstDay(p.worstDay().date(), p.worstDay().dailyReturn()));

        List<SimulationResponse.Day> days = new ArrayList<>(sim.points().size());
        for (int i = 0; i < sim.points().size(); i++) {
            ReplayPoint point = sim.points().get(i);
            DailyReturn r = sim.returns().get(i);
            days.add(new SimulationResponse.Day(
                    point.date(),
                    won(point.close()),
                    won(point.value()),
                    won(point.invested()),
                    r.dailyReturn(),
                    point.netCashFlow().signum() > 0));
        }
        SimulationResponse.StreakSet streaks = new SimulationResponse.StreakSet(
                toStats(Streaks.gains(sim.returns())),
                toStats(Streaks.losses(sim.returns())),
                purchaseUnit == null ? null : toPurchase(Streaks.purchases(sim.points(), purchaseUnit), purchaseUnit));
        return new SimulationResponse.Result(summary, pain, streaks, days);
    }

    static Streaks.Unit streakUnit(Frequency frequency) {
        return switch (frequency) {
            case DAILY -> Streaks.Unit.DAY;
            case WEEKLY -> Streaks.Unit.WEEK;
            case MONTHLY -> Streaks.Unit.MONTH;
        };
    }

    private static SimulationResponse.StreakStats toStats(Streaks.Stats stats) {
        return new SimulationResponse.StreakStats(
                stats.longest().map(SimulationCalculator::toStreak).orElse(null),
                stats.current().map(SimulationCalculator::toStreak).orElse(null));
    }

    private static SimulationResponse.PurchaseStreak toPurchase(Streaks.Stats stats, Streaks.Unit unit) {
        SimulationResponse.StreakStats s = toStats(stats);
        return new SimulationResponse.PurchaseStreak(unit.name(), s.longest(), s.current());
    }

    private static SimulationResponse.Streak toStreak(Streaks.Streak s) {
        return new SimulationResponse.Streak(s.length(), s.start(), s.end());
    }

    static long won(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
