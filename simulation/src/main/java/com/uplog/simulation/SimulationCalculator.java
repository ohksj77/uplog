package com.uplog.simulation;

import com.uplog.calc.DailyReturn;
import com.uplog.calc.PainMetrics;
import com.uplog.calc.PerformanceSummary;
import com.uplog.calc.PriceSeries;
import com.uplog.calc.PurchasePlan;
import com.uplog.calc.ReplayPoint;
import com.uplog.calc.Simulation;
import com.uplog.calc.Streaks;
import com.uplog.core.CurrencyCode;
import com.uplog.simulation.api.SimulationResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;

/**
 * 한 ETF에 대해 거치식·적립식을 같은 총 투입금으로 계산해 응답 형태로 바꾼다. 스프링 의존 없음.
 * 매수는 늘 원화 종가로 리플레이하고, 달러 기준이면 그 결과를 {@link UsdBasis}로 바꿔 다시 계산한다.
 */
public final class SimulationCalculator {

    private SimulationCalculator() {}

    public record Pair(SimulationResponse.Result lumpSum, SimulationResponse.Result recurring) {}

    /** 원화 기준. */
    public static Pair calculate(SimulationQuery query, PriceSeries prices, LocalDate from, LocalDate to) {
        return calculate(query, prices, from, to, Optional.empty());
    }

    /**
     * @param prices 원화 종가(미국 종목은 원화로 환산한 값). 매수는 이 종가에 원화 금액으로 한다.
     * @param usd 달러 기준이면 환율(과 미국 종목의 달러 종가). 원화 기준이면 empty.
     */
    public static Pair calculate(
            SimulationQuery query, PriceSeries prices, LocalDate from, LocalDate to, Optional<UsdBasis> usd) {
        NavigableSet<LocalDate> tradingDays = prices.tradingDays(from, to);
        int count = query.schedule().purchaseCount(tradingDays);
        if (count == 0) {
            throw new SimulationException(ErrorCode.NO_PRICE_DATA, "기간 안에 매수할 수 있는 거래일이 없습니다: " + from + " ~ " + to);
        }
        PlanAmounts amounts = PlanAmounts.of(query.amountType(), query.amount(), count);
        PurchasePlan lumpSum = new PurchasePlan.LumpSum(amounts.lumpSumAmount());
        PurchasePlan recurring = query.schedule().plan(amounts.perPurchaseAmount());
        Simulation lumpSumSim = Simulation.run(lumpSum, prices, from, to);
        Simulation recurringSim = Simulation.run(recurring, prices, from, to);
        CurrencyCode currency = usd.isPresent() ? CurrencyCode.USD : CurrencyCode.KRW;
        if (usd.isPresent()) {
            lumpSumSim = usd.get().convert(lumpSumSim);
            recurringSim = usd.get().convert(recurringSim);
        }
        return new Pair(
                toResult(lumpSumSim, null, currency),
                toResult(recurringSim, streakUnit(query.schedule().frequency()), currency));
    }

    /**
     * @param purchaseUnit 적립 연속을 셀 주기. 거치식이면 null.
     * @param currency 금액을 반올림할 통화(KRW 원 단위, USD 센트 단위)
     */
    static SimulationResponse.Result toResult(Simulation sim, Streaks.Unit purchaseUnit, CurrencyCode currency) {
        PerformanceSummary s = sim.summary();
        BigDecimal finalValue = money(s.finalValue(), currency);
        BigDecimal invested = money(s.invested(), currency);
        // profit은 반올림한 두 값의 차로 둬서 화면에서 finalValue − invested와 항상 맞게 한다.
        SimulationResponse.Summary summary = new SimulationResponse.Summary(
                finalValue, invested, finalValue.subtract(invested), s.totalReturn(), s.twr(), s.annualizedTwr());

        PainMetrics p = sim.pain();
        SimulationResponse.Pain pain = new SimulationResponse.Pain(
                new SimulationResponse.MaxDrawdown(p.maxDrawdown().rate(), p.maxDrawdown().peak(), p.maxDrawdown().trough()),
                new SimulationResponse.MaxDrawdownAmount(
                        money(p.maxProfitDrawdown().amount(), currency), p.maxProfitDrawdown().peak(), p.maxProfitDrawdown().trough()),
                p.longestUnderwater()
                        .map(u -> new SimulationResponse.Underwater(u.start(), u.end(), u.recovered(), u.days()))
                        .orElse(null),
                new SimulationResponse.WorstDay(p.worstDay().date(), p.worstDay().dailyReturn()),
                new SimulationResponse.WorstDayAmount(p.worstDayAmount().date(), money(p.worstDayAmount().pnl(), currency)));

        List<SimulationResponse.Day> days = new ArrayList<>(sim.points().size());
        for (int i = 0; i < sim.points().size(); i++) {
            ReplayPoint point = sim.points().get(i);
            DailyReturn r = sim.returns().get(i);
            days.add(new SimulationResponse.Day(
                    point.date(),
                    money(point.close(), currency),
                    money(point.value(), currency),
                    money(point.invested(), currency),
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

    /** 통화의 표시 단위로 반올림한다(KRW 0자리, USD 2자리). JSON에는 그 자릿수 그대로 숫자로 나간다. */
    static BigDecimal money(BigDecimal amount, CurrencyCode currency) {
        return amount.setScale(currency.scale(), RoundingMode.HALF_UP);
    }
}
