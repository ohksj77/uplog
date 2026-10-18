package com.uplog.portfolio;

import com.uplog.calc.PriceSeries;
import com.uplog.calc.portfolio.AccountFlow;
import com.uplog.calc.portfolio.AccountLog;
import com.uplog.calc.portfolio.Dividend;
import com.uplog.calc.portfolio.Execution;
import com.uplog.calc.portfolio.HoldingPeriods;
import com.uplog.calc.portfolio.PortfolioHistory;
import com.uplog.calc.portfolio.PortfolioReplay;
import com.uplog.core.CurrencyCode;
import com.uplog.marketdata.Instrument;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * 한 계좌의 원시 데이터를 리플레이한 결과 묶음. 시세·계좌 데이터 버전이 같으면 그대로 캐시해 모든 엔드포인트가 나눠 쓴다.
 *
 * <p>계좌 거래일은 거래한 종목들의 시세 날짜(와 환율 고시일)의 합집합이고, 첫 입금·체결일부터 모든 종목 시세가 있는
 * 마지막 날까지다. 연결일 전은 BACKFILLED, 수집 실패일은 MISSING, 나머지는 COLLECTED.
 */
public record PortfolioSnapshot(
        PortfolioRepository.Account account,
        Map<String, Instrument> instruments,
        Map<String, CurrencyCode> currencies,
        Map<String, PriceSeries> closes,
        PriceSeries usdKrw,
        List<Execution> trades,
        List<AccountFlow> flows,
        List<Dividend> dividends,
        PortfolioHistory history,
        List<AccountLog.Entry> log,
        List<AccountLog.Entry> observed,
        Map<LocalDate, DaySource> sources,
        List<HoldingPeriods.Period> periods) {

    /** 원시 데이터 + 시세 → 리플레이. closes에는 계좌 구간보다 앞선 시세가 있어도 된다(종목 차트용). */
    public static PortfolioSnapshot build(
            PortfolioRepository.Account account,
            Map<String, Instrument> instruments,
            Map<String, PriceSeries> closes,
            PriceSeries usdKrw,
            List<Execution> trades,
            List<AccountFlow> flows,
            List<Dividend> dividends,
            Set<LocalDate> gaps) {
        Map<String, CurrencyCode> currencies = new TreeMap<>();
        instruments.forEach((code, i) -> currencies.put(code, i.currency()));
        LocalDate first = Stream.concat(trades.stream().map(Execution::date), flows.stream().map(AccountFlow::date))
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("계좌에 입금·체결 기록이 없습니다"));
        List<PriceSeries> calendars = new ArrayList<>(closes.values());
        boolean hasUsd = currencies.containsValue(CurrencyCode.USD);
        if (hasUsd) {
            if (usdKrw == null) {
                throw new IllegalStateException("해외 종목이 있는데 USD/KRW 환율이 없습니다(계좌 " + account.id() + ")");
            }
            calendars.add(usdKrw);
        }
        LocalDate last = calendars.stream().map(PriceSeries::lastDate).min(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("계좌 종목의 시세가 없습니다"));
        TreeSet<LocalDate> days = new TreeSet<>();
        for (PriceSeries s : closes.values()) {
            days.addAll(s.tradingDays(first, last));
        }
        if (days.isEmpty()) {
            throw new IllegalStateException("계좌 구간에 거래일이 없습니다: " + first + " ~ " + last);
        }

        PortfolioHistory history = PortfolioReplay.replay(new PortfolioReplay.Input(
                List.copyOf(days), currencies, closes, hasUsd ? usdKrw : null, trades, flows, dividends));
        List<AccountLog.Entry> log = AccountLog.of(history, gaps);
        Map<LocalDate, DaySource> sources = new LinkedHashMap<>();
        for (AccountLog.Entry e : log) {
            sources.put(e.date(), e.missing() ? DaySource.MISSING
                    : e.date().isBefore(account.connectedAt()) ? DaySource.BACKFILLED : DaySource.COLLECTED);
        }
        return new PortfolioSnapshot(account, Map.copyOf(instruments), currencies, Map.copyOf(closes), usdKrw,
                List.copyOf(trades), List.copyOf(flows), List.copyOf(dividends), history, log,
                AccountLog.observed(log), sources, HoldingPeriods.of(history));
    }

    public LocalDate firstDate() {
        return history.first().date();
    }

    public LocalDate lastDate() {
        return history.last().date();
    }

    public AccountLog.Entry lastObserved() {
        return observed.getLast();
    }

    public Optional<AccountLog.Entry> observedOn(LocalDate date) {
        return observed.stream().filter(e -> e.date().equals(date)).findFirst();
    }

    /** 원화 종목은 null. */
    public PriceSeries fxFor(String code) {
        return currencies.get(code) == CurrencyCode.USD ? usdKrw : null;
    }
}
