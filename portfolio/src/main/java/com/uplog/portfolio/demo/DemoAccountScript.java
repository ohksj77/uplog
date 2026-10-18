package com.uplog.portfolio.demo;

import com.uplog.calc.PriceSeries;
import com.uplog.calc.Trade;
import com.uplog.calc.portfolio.AccountFlow;
import com.uplog.calc.portfolio.Dividend;
import com.uplog.calc.portfolio.Execution;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 합성 데모 계좌의 원시 데이터. 시세가 같으면 늘 같은 결과다(난수 없음, 대본 + 그날 종가).
 *
 * <ul>
 *   <li>첫날 1,000만 원 입금, 이후 매달 첫 거래일 100만 원 입금, 두 번 출금.
 *   <li>매달 입금일에 샘플 코스피200 ETF 50만 원, 샘플 S&P500 ETF 30만·50만 원(격월 번갈아) 적립.
 *   <li>대본 매매: 단타(7일 이하)·스윙·중기·1년 넘는 라운드트립, 일부 매도, 아직 보유 중인 포지션.
 *   <li>매수는 가진 현금 안에서만. 수수료 국내 0.015%(원 미만 버림)·미국 0.25%(센트 반올림), 국내 매도 거래세 0.18%.
 *   <li>분기 배당(1·4·7·10월 20일 이후 첫 거래일): 샘플전자, 샘플 코스피200 ETF(세후 84.6%), 샘플 S&P500 ETF(세후 85%).
 *   <li>마지막 날 약 3개월 전이 연결일(그 전은 역산), 최근 평일 이틀은 수집 실패.
 * </ul>
 */
public final class DemoAccountScript {

    public static final String NAME = "데모 계좌";

    private static final BigDecimal KR_FEE = new BigDecimal("0.00015");
    private static final BigDecimal KR_SELL_TAX = new BigDecimal("0.0018");
    private static final BigDecimal US_FEE = new BigDecimal("0.0025");

    private DemoAccountScript() {}

    public record Plan(
            LocalDate connectedAt, List<Execution> trades, List<AccountFlow> flows, List<Dividend> dividends, List<LocalDate> gaps) {}

    private enum Kind {
        BUY,
        SELL_PART,
        SELL_ALL
    }

    /** @param value 매수면 원화 예산, 일부 매도면 파는 비율 */
    private record Action(int day, String code, Kind kind, double value) {}

    private static final List<Action> SCRIPT = List.of(
            buy(1, "S00003", 3_000_000), buy(1, "SMPS", 2_000_000), buy(3, "S00001", 2_000_000),
            buy(10, "SMPT", 1_200_000), sellAll(14, "SMPT"),
            buy(30, "S00002", 1_000_000), sellAll(33, "S00002"),
            buy(45, "SMPC", 1_200_000), sellPart(70, "SMPC", 0.5), sellAll(95, "SMPC"),
            buy(60, "S00004", 1_000_000), sellAll(150, "S00004"),
            sellPart(120, "S00001", 0.5),
            buy(140, "SMPT", 1_000_000), sellAll(145, "SMPT"),
            buy(200, "S00002", 1_200_000), sellAll(230, "S00002"),
            buy(210, "SMPE", 1_200_000), sellPart(300, "SMPE", 0.4), sellAll(330, "SMPE"),
            buy(260, "S00001", 800_000),
            buy(350, "SMPC", 1_500_000), sellAll(356, "SMPC"),
            buy(380, "S00004", 1_000_000), sellAll(395, "S00004"),
            sellAll(400, "S00001"), buy(410, "S00001", 1_000_000),
            buy(420, "SMPT", 1_500_000),
            buy(440, "SMPE", 1_000_000), sellAll(446, "SMPE"),
            buy(470, "S00002", 800_000),
            buy(480, "SMPC", 1_000_000), sellAll(500, "SMPC"),
            sellPart(505, "S00003", 0.2));

    /**
     * @param closes 종목코드 → 현지 종가(샘플 8종)
     * @param end 마지막 시세일
     */
    public static Plan generate(Map<String, PriceSeries> closes, PriceSeries usdKrw, LocalDate end) {
        List<LocalDate> days = List.copyOf(closes.get("S00001").tradingDays(end.minusYears(2), end));
        int last = days.size() - 1;
        Map<Integer, List<Action>> script = new TreeMap<>();
        for (Action a : SCRIPT) {
            script.computeIfAbsent(Math.min(a.day(), last - 1), k -> new ArrayList<>()).add(a);
        }
        Set<Integer> monthStarts = new HashSet<>();
        for (int i = 1; i <= last; i++) {
            if (days.get(i).getMonthValue() != days.get(i - 1).getMonthValue()) {
                monthStarts.add(i);
            }
        }

        State st = new State(closes, usdKrw);
        int month = 0;
        for (int i = 0; i <= last; i++) {
            LocalDate day = days.get(i);
            if (i == 0) {
                st.deposit(day, 10_000_000);
            }
            if (monthStarts.contains(i)) {
                month++;
                st.deposit(day, 1_000_000);
            }
            if (monthStarts.contains(i - 5) && (month == 9 || month == 18)) {
                st.withdraw(day, month == 9 ? 2_000_000 : 1_500_000);
            }
            List<Action> today = new ArrayList<>(script.getOrDefault(i, List.of()));
            if (monthStarts.contains(i)) {
                today.add(buy(i, "S00003", 500_000));
                today.add(buy(i, "SMPS", month % 2 == 0 ? 500_000 : 300_000));
            }
            today.stream().filter(a -> a.kind() != Kind.BUY).forEach(a -> st.sell(day, a));
            today.stream().filter(a -> a.kind() == Kind.BUY).forEach(a -> st.buy(day, a));
        }

        LocalDate connectedAt = days.stream().filter(d -> !d.isBefore(end.minusMonths(3))).findFirst().orElseThrow();
        List<LocalDate> gaps = new ArrayList<>();
        for (int i = last - 4; i > 0 && gaps.size() < 2; i -= 5) {
            if (!st.eventDays.contains(days.get(i))) {
                gaps.add(days.get(i));
            }
        }
        return new Plan(connectedAt, List.copyOf(st.trades), List.copyOf(st.flows), List.copyOf(st.dividends),
                gaps.reversed());
    }

    private static Action buy(int day, String code, long budget) {
        return new Action(day, code, Kind.BUY, budget);
    }

    private static Action sellPart(int day, String code, double fraction) {
        return new Action(day, code, Kind.SELL_PART, fraction);
    }

    private static Action sellAll(int day, String code) {
        return new Action(day, code, Kind.SELL_ALL, 1.0);
    }

    /** 대본을 따라가며 현금과 수량을 맞춰 본다(가진 현금 안에서만 사고, 가진 수량 안에서만 판다). */
    private static final class State {
        final Map<String, PriceSeries> closes;
        final PriceSeries usdKrw;
        final Map<String, Long> quantities = new HashMap<>();
        final List<Execution> trades = new ArrayList<>();
        final List<AccountFlow> flows = new ArrayList<>();
        final List<Dividend> dividends = new ArrayList<>();
        final Set<LocalDate> eventDays = new HashSet<>();
        BigDecimal cash = BigDecimal.ZERO;

        State(Map<String, PriceSeries> closes, PriceSeries usdKrw) {
            this.closes = closes;
            this.usdKrw = usdKrw;
        }

        boolean usd(String code) {
            return !code.startsWith("S0");
        }

        BigDecimal fx(String code, LocalDate day) {
            return usd(code) ? usdKrw.closeOnOrBefore(day).orElseThrow() : BigDecimal.ONE;
        }

        void deposit(LocalDate day, long amount) {
            flows.add(new AccountFlow(day, AccountFlow.Type.DEPOSIT, BigDecimal.valueOf(amount)));
            cash = cash.add(BigDecimal.valueOf(amount));
            eventDays.add(day);
        }

        void withdraw(LocalDate day, long amount) {
            long possible = Math.min(amount, cash.longValue() / 100_000 * 100_000);
            if (possible > 0) {
                flows.add(new AccountFlow(day, AccountFlow.Type.WITHDRAWAL, BigDecimal.valueOf(possible)));
                cash = cash.subtract(BigDecimal.valueOf(possible));
                eventDays.add(day);
            }
        }

        void buy(LocalDate day, Action a) {
            BigDecimal price = closes.get(a.code()).closeOn(day);
            BigDecimal fx = fx(a.code(), day);
            BigDecimal unitKrw = price.multiply(fx);
            BigDecimal budget = BigDecimal.valueOf(a.value()).min(cash);
            long qty = budget.divide(unitKrw, 0, RoundingMode.DOWN).longValue();
            while (qty > 0 && cost(a.code(), qty, price, fx).compareTo(cash) > 0) {
                qty--;
            }
            if (qty == 0) {
                return;
            }
            BigDecimal gross = price.multiply(BigDecimal.valueOf(qty));
            BigDecimal fee = fee(a.code(), gross);
            trades.add(new Execution(day, a.code(), Trade.Side.BUY, BigDecimal.valueOf(qty), price, fx, fee, BigDecimal.ZERO));
            cash = cash.subtract(cost(a.code(), qty, price, fx));
            quantities.merge(a.code(), qty, Long::sum);
            eventDays.add(day);
        }

        void sell(LocalDate day, Action a) {
            long held = quantities.getOrDefault(a.code(), 0L);
            long qty = a.kind() == Kind.SELL_ALL ? held : Math.max(1, (long) Math.floor(held * a.value()));
            if (held == 0 || qty == 0) {
                return;
            }
            BigDecimal price = closes.get(a.code()).closeOn(day);
            BigDecimal fx = fx(a.code(), day);
            BigDecimal gross = price.multiply(BigDecimal.valueOf(qty));
            BigDecimal fee = fee(a.code(), gross);
            BigDecimal tax = usd(a.code()) ? BigDecimal.ZERO : gross.multiply(KR_SELL_TAX).setScale(0, RoundingMode.DOWN);
            trades.add(new Execution(day, a.code(), Trade.Side.SELL, BigDecimal.valueOf(qty), price, fx, fee, tax));
            cash = cash.add(gross.subtract(fee).subtract(tax).multiply(fx));
            quantities.put(a.code(), held - qty);
            eventDays.add(day);
        }

        BigDecimal cost(String code, long qty, BigDecimal price, BigDecimal fx) {
            BigDecimal gross = price.multiply(BigDecimal.valueOf(qty));
            return gross.add(fee(code, gross)).multiply(fx);
        }

        BigDecimal fee(String code, BigDecimal grossLocal) {
            return usd(code)
                    ? grossLocal.multiply(US_FEE).setScale(2, RoundingMode.HALF_UP)
                    : grossLocal.multiply(KR_FEE).setScale(0, RoundingMode.DOWN);
        }
    }
}
