package com.uplog.calc.portfolio;

import com.uplog.calc.PriceSeries;
import com.uplog.calc.Trade;
import com.uplog.core.CurrencyCode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * 체결·입출금·배당을 일별 종가와 환율에 다시 돌려 거래일마다 계좌 상태를 만든다.
 * 수집이 시작되기 전 기간의 역산(backfill)과, 수집한 원시 데이터로 파생 지표를 다시 계산할 때 같은 함수를 쓴다.
 *
 * <p>규칙
 * <ul>
 *   <li>모든 일은 장 마감 시점에 일어난다고 본다. 휴장일 사건은 다음 계좌 거래일로 옮기고, 마지막 날 뒤의 사건은 버린다.
 *   <li>현금은 원화 하나다. 해외 종목은 체결 환율로 원화에서 바로 사고판다.
 *   <li>평가는 그날(없으면 직전) 종가 × 그날(없으면 직전) USD/KRW.
 *   <li>같은 날 체결은 입력 순서대로 적용한다(이동평균 원가가 순서에 따라 달라진다).
 * </ul>
 */
public final class PortfolioReplay {

    private PortfolioReplay() {}

    /**
     * @param days 계좌 거래일, 오름차순
     * @param currencies 종목코드 → 통화(KRW | USD)
     * @param closes 종목코드 → 현지 통화 종가
     * @param usdKrw USD/KRW 환율. 달러 종목이 없으면 null.
     */
    public record Input(
            List<LocalDate> days,
            Map<String, CurrencyCode> currencies,
            Map<String, PriceSeries> closes,
            PriceSeries usdKrw,
            List<Execution> executions,
            List<AccountFlow> flows,
            List<Dividend> dividends) {

        public Input {
            days = List.copyOf(days);
            currencies = Map.copyOf(currencies);
            closes = Map.copyOf(closes);
            executions = List.copyOf(executions);
            flows = List.copyOf(flows);
            dividends = List.copyOf(dividends);
        }
    }

    private record Quote(BigDecimal priceLocal, BigDecimal fxRate) {}

    public static PortfolioHistory replay(Input in) {
        if (in.days().isEmpty()) {
            throw new IllegalArgumentException("계좌 거래일이 비어 있습니다");
        }
        TreeSet<LocalDate> calendar = new TreeSet<>(in.days());
        if (calendar.size() != in.days().size() || !calendar.stream().toList().equals(in.days())) {
            throw new IllegalArgumentException("계좌 거래일은 중복 없이 오름차순이어야 합니다");
        }
        Map<LocalDate, List<Execution>> executions = byDay(calendar, in.executions(), Execution::date);
        Map<LocalDate, List<AccountFlow>> flows = byDay(calendar, in.flows(), AccountFlow::date);
        Map<LocalDate, List<Dividend>> dividends = byDay(calendar, in.dividends(), Dividend::date);

        Map<String, Position> positions = new TreeMap<>();
        Map<String, InstrumentDay> yesterday = new HashMap<>();
        List<PortfolioDay> days = new ArrayList<>(calendar.size());
        List<RealizedSale> sales = new ArrayList<>();
        BigDecimal cash = BigDecimal.ZERO;
        BigDecimal prevValue = BigDecimal.ZERO;
        BigDecimal netDeposits = BigDecimal.ZERO;
        double index = 1.0;

        for (LocalDate day : calendar) {
            List<Execution> todayExecutions = executions.getOrDefault(day, List.of());
            List<AccountFlow> todayFlows = flows.getOrDefault(day, List.of());
            List<Dividend> todayDividends = dividends.getOrDefault(day, List.of());

            BigDecimal netCashFlow = todayFlows.stream().map(AccountFlow::signedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            cash = cash.add(netCashFlow);
            netDeposits = netDeposits.add(netCashFlow);

            Map<String, Activity> activity = new TreeMap<>();
            for (Dividend d : todayDividends) {
                Activity a = activity.computeIfAbsent(d.instrument(), k -> new Activity());
                a.dividends = a.dividends.add(d.amount());
                cash = cash.add(d.amount());
            }
            Map<String, Position> before = new HashMap<>(positions);
            for (Execution e : todayExecutions) {
                requireKnown(in, e.instrument());
                Activity a = activity.computeIfAbsent(e.instrument(), k -> new Activity());
                Position p = positions.getOrDefault(e.instrument(), Position.EMPTY);
                a.fees = a.fees.add(e.feeKrw());
                a.taxes = a.taxes.add(e.taxKrw());
                if (e.side() == Trade.Side.BUY) {
                    positions.put(e.instrument(), p.buy(e.quantity(), e.grossLocal(), e.grossKrw()));
                    cash = cash.subtract(e.grossKrw()).subtract(e.feeKrw()).subtract(e.taxKrw());
                    a.buyQuantity = a.buyQuantity.add(e.quantity());
                    a.buyLocal = a.buyLocal.add(e.grossLocal());
                    a.buyKrw = a.buyKrw.add(e.grossKrw());
                } else {
                    BigDecimal realized = e.grossKrw().subtract(p.costKrwOf(e.quantity())).subtract(e.feeKrw()).subtract(e.taxKrw());
                    sales.add(new RealizedSale(sales.size() + 1, e, day, p.avgCostLocal(), p.avgFxRate(), realized));
                    positions.put(e.instrument(), p.sell(e.quantity()));
                    cash = cash.add(e.grossKrw()).subtract(e.feeKrw()).subtract(e.taxKrw());
                    a.sellQuantity = a.sellQuantity.add(e.quantity());
                    a.sellLocal = a.sellLocal.add(e.grossLocal());
                    a.sellKrw = a.sellKrw.add(e.grossKrw());
                }
            }

            TreeSet<String> codes = new TreeSet<>(activity.keySet());
            before.forEach((code, p) -> {
                if (p.isOpen()) {
                    codes.add(code);
                }
            });
            Map<String, InstrumentDay> today = new TreeMap<>();
            BigDecimal value = cash;
            BigDecimal fees = BigDecimal.ZERO;
            BigDecimal taxes = BigDecimal.ZERO;
            BigDecimal dividendTotal = BigDecimal.ZERO;
            for (String code : codes) {
                Activity a = activity.getOrDefault(code, new Activity());
                Position prev = before.getOrDefault(code, Position.EMPTY);
                Position now = positions.getOrDefault(code, Position.EMPTY);
                InstrumentDay y = yesterday.get(code);
                Quote q = quote(in, code, day, now.isOpen() || prev.isOpen());
                BigDecimal prevPrice = y != null && prev.isOpen() ? y.priceLocal() : q.priceLocal();
                BigDecimal prevFx = y != null && prev.isOpen() ? y.fxRate() : q.fxRate();
                BigDecimal prevInstrumentValue = y != null && prev.isOpen() ? y.value() : BigDecimal.ZERO;
                BigDecimal instrumentValue = now.quantity().multiply(q.priceLocal()).multiply(q.fxRate());
                InstrumentDay d = new InstrumentDay(
                        code, prev, prevPrice, prevFx, prevInstrumentValue,
                        now, q.priceLocal(), q.fxRate(), instrumentValue,
                        a.buyQuantity, a.buyLocal, a.buyKrw, a.sellQuantity, a.sellLocal, a.sellKrw,
                        a.fees, a.taxes, a.dividends);
                today.put(code, d);
                value = value.add(instrumentValue);
                fees = fees.add(a.fees);
                taxes = taxes.add(a.taxes);
                dividendTotal = dividendTotal.add(a.dividends);
            }

            BigDecimal pnl = value.subtract(netCashFlow).subtract(prevValue);
            double r = prevValue.signum() > 0 ? pnl.divide(prevValue, MathContext.DECIMAL64).doubleValue() : 0.0;
            index *= 1.0 + r;
            days.add(new PortfolioDay(day, cash, value, netCashFlow, pnl, r, index, netDeposits, today,
                    dividendTotal, fees, taxes, todayExecutions, todayFlows, todayDividends));
            yesterday = today;
            prevValue = value;
            positions.values().removeIf(p -> !p.isOpen());
        }
        return new PortfolioHistory(days, sales);
    }

    private static final class Activity {
        BigDecimal buyQuantity = BigDecimal.ZERO;
        BigDecimal buyLocal = BigDecimal.ZERO;
        BigDecimal buyKrw = BigDecimal.ZERO;
        BigDecimal sellQuantity = BigDecimal.ZERO;
        BigDecimal sellLocal = BigDecimal.ZERO;
        BigDecimal sellKrw = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        BigDecimal taxes = BigDecimal.ZERO;
        BigDecimal dividends = BigDecimal.ZERO;
    }

    private static void requireKnown(Input in, String code) {
        if (!in.currencies().containsKey(code)) {
            throw new IllegalArgumentException("통화를 모르는 종목입니다: " + code);
        }
    }

    private static Quote quote(Input in, String code, LocalDate day, boolean required) {
        CurrencyCode currency = in.currencies().get(code);
        PriceSeries series = in.closes().get(code);
        BigDecimal price = series == null ? null : series.closeOnOrBefore(day).orElse(null);
        BigDecimal fx = BigDecimal.ONE;
        if (currency == CurrencyCode.USD) {
            fx = in.usdKrw() == null ? null : in.usdKrw().closeOnOrBefore(day).orElse(null);
        }
        if (price == null || fx == null) {
            if (required) {
                throw new IllegalArgumentException("평가할 시세나 환율이 없습니다: " + code + " " + day);
            }
            return new Quote(price == null ? BigDecimal.ZERO : price, fx == null ? BigDecimal.ONE : fx);
        }
        return new Quote(price, fx);
    }

    private static <T> Map<LocalDate, List<T>> byDay(TreeSet<LocalDate> calendar, List<T> items, Function<T, LocalDate> date) {
        Map<LocalDate, List<T>> map = new HashMap<>();
        items.stream()
                .sorted(Comparator.comparing(date))
                .forEach(item -> {
                    LocalDate day = calendar.ceiling(Objects.requireNonNull(date.apply(item)));
                    if (day != null) {
                        map.computeIfAbsent(day, k -> new ArrayList<>()).add(item);
                    }
                });
        return map;
    }
}
