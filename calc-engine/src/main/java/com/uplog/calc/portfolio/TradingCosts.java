package com.uplog.calc.portfolio;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * 숨은 비용: 체결 수수료 + 세금(원). 체결마다 원 단위로 반올림한 뒤 더한다(하루 상세의 체결 fee·tax와 같다).
 * 기록일(MISSING 제외)만 쓴다: 수집 실패일의 체결은 {@link AccountLog}가 다음 기록일에 몰아 넣은 대로 그날의 달에 센다.
 * 그래서 그해 손익 합(수익 로그의 totalPnl)과 그해 비용이 같은 날들 위에서 맞물린다.
 */
public final class TradingCosts {

    private TradingCosts() {}

    public record Cost(long fee, long tax) {

        public static final Cost ZERO = new Cost(0, 0);

        public long cost() {
            return fee + tax;
        }

        Cost plus(Execution e) {
            return new Cost(fee + PnlWaterfall.won(e.feeKrw()), tax + PnlWaterfall.won(e.taxKrw()));
        }
    }

    public record Month(YearMonth month, Cost cost) {}

    /** @param trades 그해 체결 수 */
    public record ByInstrument(String instrument, Cost cost, int trades) {}

    /**
     * @param pnl 그해 손익 합(원 단위 하루 손익의 합)
     * @param profitBeforeCost pnl + 그해 비용
     * @param costShare 비용 / profitBeforeCost. profitBeforeCost ≤ 0이면 empty.
     * @param months 그해 기록일이 있는 달, 오래된 순
     * @param byInstrument 그해 체결이 있었던 종목, 비용 큰 순(같으면 종목코드순)
     */
    public record Result(
            int year, Cost total, Cost allTime, long pnl, long profitBeforeCost, Optional<Double> costShare,
            List<Month> months, List<ByInstrument> byInstrument) {

        public Result {
            months = List.copyOf(months);
            byInstrument = List.copyOf(byInstrument);
        }
    }

    /** @param log {@link AccountLog#of}의 전체 로그(MISSING 포함) */
    public static Result of(List<AccountLog.Entry> log, int year) {
        Cost total = Cost.ZERO;
        Cost allTime = Cost.ZERO;
        long pnl = 0;
        Map<YearMonth, Cost> months = new TreeMap<>();
        Map<String, Cost> costs = new TreeMap<>();
        Map<String, Integer> trades = new TreeMap<>();
        for (AccountLog.Entry e : log) {
            if (e.missing()) {
                continue;
            }
            boolean inYear = e.date().getYear() == year;
            YearMonth ym = YearMonth.from(e.date());
            if (inYear) {
                pnl += PnlWaterfall.won(e.pnl());
                months.putIfAbsent(ym, Cost.ZERO);
            }
            for (Execution x : e.executions()) {
                allTime = allTime.plus(x);
                if (!inYear) {
                    continue;
                }
                total = total.plus(x);
                months.put(ym, months.get(ym).plus(x));
                costs.put(x.instrument(), costs.getOrDefault(x.instrument(), Cost.ZERO).plus(x));
                trades.merge(x.instrument(), 1, Integer::sum);
            }
        }
        List<ByInstrument> byInstrument = new ArrayList<>();
        costs.forEach((code, c) -> byInstrument.add(new ByInstrument(code, c, trades.get(code))));
        byInstrument.sort(Comparator.comparingLong((ByInstrument b) -> b.cost().cost()).reversed()
                .thenComparing(ByInstrument::instrument));
        long beforeCost = pnl + total.cost();
        Optional<Double> share = beforeCost <= 0 ? Optional.empty()
                : Optional.of(BigDecimal.valueOf(total.cost()).divide(BigDecimal.valueOf(beforeCost), MathContext.DECIMAL64).doubleValue());
        return new Result(year, total, allTime, pnl, beforeCost, share,
                months.entrySet().stream().map(m -> new Month(m.getKey(), m.getValue())).toList(), byInstrument);
    }
}
