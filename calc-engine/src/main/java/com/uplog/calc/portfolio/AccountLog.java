package com.uplog.calc.portfolio;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 화면에 보이는 일별 로그. 수집에 실패한 날(MISSING)은 값을 0으로 비우고, 그 사이의 입출금·체결·손익은
 * 다음 기록일에 몰아 넣는다. 그래서 기록일만 이어 보면 {@code pnl = value − netCashFlow − 직전 기록일 value}가 늘 맞고,
 * 종목별 기여 합 + 배당 − 수수료 − 세금 = pnl도 맞다.
 */
public final class AccountLog {

    private AccountLog() {}

    /** @param pnl 종목 손익(원, 환율 포함) / dailyReturn pnl ÷ (직전 기록일 평가금 + 매수 금액) / weight 그날 평가금 비중 */
    public record Contribution(String instrument, BigDecimal pnl, double dailyReturn, double weight) {}

    /**
     * @param missing 수집 실패일. value·pnl·netCashFlow는 0이고 twrIndex·netDeposits는 직전 값을 잇는다.
     * @param pnl value − netCashFlow − 직전 기록일 value
     * @param contributions 절댓값 큰 순
     */
    public record Entry(
            LocalDate date,
            boolean missing,
            BigDecimal value,
            BigDecimal netCashFlow,
            BigDecimal pnl,
            double dailyReturn,
            double twrIndex,
            BigDecimal netDeposits,
            List<Contribution> contributions,
            BigDecimal dividends,
            BigDecimal fees,
            BigDecimal taxes,
            List<Execution> executions,
            List<AccountFlow> flows,
            List<Dividend> dividendEvents) {

        public Entry {
            contributions = List.copyOf(contributions);
            executions = List.copyOf(executions);
            flows = List.copyOf(flows);
            dividendEvents = List.copyOf(dividendEvents);
        }

        public BigDecimal profit() {
            return value.subtract(netDeposits);
        }
    }

    public static List<Entry> of(PortfolioHistory history, Set<LocalDate> missing) {
        List<Entry> entries = new ArrayList<>(history.days().size());
        List<PortfolioDay> gap = new ArrayList<>();
        BigDecimal lastValue = BigDecimal.ZERO;
        BigDecimal lastDeposits = BigDecimal.ZERO;
        double index = 1.0;
        for (PortfolioDay day : history.days()) {
            gap.add(day);
            if (missing.contains(day.date())) {
                entries.add(new Entry(day.date(), true, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0.0, index,
                        lastDeposits, List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        List.of(), List.of(), List.of()));
                continue;
            }
            BigDecimal netCashFlow = sum(gap, PortfolioDay::netCashFlow);
            BigDecimal pnl = day.value().subtract(netCashFlow).subtract(lastValue);
            double r = lastValue.signum() > 0 ? pnl.divide(lastValue, MathContext.DECIMAL64).doubleValue() : 0.0;
            index *= 1.0 + r;
            entries.add(new Entry(day.date(), false, day.value(), netCashFlow, pnl, r, index, day.netDeposits(),
                    contributions(gap, day),
                    sum(gap, PortfolioDay::dividends), sum(gap, PortfolioDay::fees), sum(gap, PortfolioDay::taxes),
                    gap.stream().flatMap(d -> d.executions().stream()).toList(),
                    gap.stream().flatMap(d -> d.flows().stream()).toList(),
                    gap.stream().flatMap(d -> d.dividendEvents().stream()).toList()));
            lastValue = day.value();
            lastDeposits = day.netDeposits();
            gap.clear();
        }
        return List.copyOf(entries);
    }

    /** 기록일만(MISSING 제외). */
    public static List<Entry> observed(List<Entry> entries) {
        return entries.stream().filter(e -> !e.missing()).toList();
    }

    private static List<Contribution> contributions(List<PortfolioDay> gap, PortfolioDay today) {
        Map<String, BigDecimal[]> merged = new LinkedHashMap<>(); // pnl, base
        for (PortfolioDay d : gap) {
            for (InstrumentDay i : d.instruments().values()) {
                if (!i.hadActivity()) {
                    continue;
                }
                BigDecimal[] acc = merged.computeIfAbsent(i.instrument(), k -> new BigDecimal[] {BigDecimal.ZERO, i.previousValue()});
                acc[0] = acc[0].add(i.contribution());
                acc[1] = acc[1].add(i.buyKrw());
            }
        }
        List<Contribution> result = new ArrayList<>();
        merged.forEach((code, acc) -> {
            InstrumentDay now = today.instruments().get(code);
            BigDecimal value = now == null ? BigDecimal.ZERO : now.value();
            double r = acc[1].signum() == 0 ? 0.0 : acc[0].divide(acc[1], MathContext.DECIMAL64).doubleValue();
            double w = today.value().signum() == 0 ? 0.0 : value.divide(today.value(), MathContext.DECIMAL64).doubleValue();
            result.add(new Contribution(code, acc[0], r, w));
        });
        result.sort(Comparator.comparing((Contribution c) -> c.pnl().abs()).reversed().thenComparing(Contribution::instrument));
        return result;
    }

    private static BigDecimal sum(List<PortfolioDay> days, Function<PortfolioDay, BigDecimal> f) {
        return days.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
