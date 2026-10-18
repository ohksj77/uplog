package com.uplog.simulation.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/simulations 응답. 필드 이름·형태는 docs/api/simulation.md와 web/src/lib/api/types.ts가 함께 따른다.
 * 금액은 {@code currency} 기준: KRW면 원 단위 반올림한 정수(scale 0), USD면 센트 단위 반올림한 달러(scale 2).
 * 비율은 소수, 날짜는 ISO(YYYY-MM-DD).
 *
 * @param from 기간 시작일(종료일 − 기간). 거래일이 아닐 수 있고, 첫 매수는 그날 또는 다음 거래일이다.
 * @param to 기간 종료일. 여러 ETF를 비교하면 각 ETF 마지막 시세일 중 가장 이른 날로 맞춘다.
 * @param currency 응답 금액의 기준 통화 {@code KRW} | {@code USD}. 매수(원화 금액·날짜)는 같고 나타내는 통화만 다르다.
 */
public record SimulationResponse(
        LocalDate from, LocalDate to, String currency, List<EtfResult> results, String disclaimer) {

    public static final String DISCLAIMER = "과거 수익률은 미래 수익을 보장하지 않습니다. 이 계산은 투자 권유가 아닙니다.";

    public SimulationResponse {
        results = List.copyOf(results);
    }

    public record EtfResult(Etf etf, Result lumpSum, Result recurring) {}

    /**
     * 시뮬레이션한 종목(ETF 또는 주식).
     *
     * @param market {@code KR} | {@code US}
     * @param type {@code ETF} | {@code STOCK}
     * @param currency 종목의 거래 통화. 응답의 금액·종가는 종목 통화와 상관없이 응답의 {@code currency} 기준이다.
     */
    public record Etf(String code, String name, String market, String type, String currency) {}

    public record Result(Summary summary, Pain pain, StreakSet streaks, List<Day> days) {

        public Result {
            days = List.copyOf(days);
        }
    }

    public record Summary(
            BigDecimal finalValue,
            BigDecimal invested,
            BigDecimal profit,
            double totalReturn,
            double twr,
            double annualizedTwr) {}

    /**
     * 하락 폭과 가장 아팠던 날은 두 기준: 수익률(TWR, 입출금 제외)과 금액(평가손익·하루 손익).
     * 둘 다 기준 통화로 다시 계산하므로 USD 기준이면 환율 효과가 빠진다.
     *
     * @param underwater 평가금액이 원금 아래로 내려간 적이 없으면 null
     */
    public record Pain(
            MaxDrawdown maxDrawdown,
            MaxDrawdownAmount maxDrawdownAmount,
            Underwater underwater,
            WorstDay worstDay,
            WorstDayAmount worstDayAmount) {}

    public record MaxDrawdown(double rate, LocalDate peak, LocalDate trough) {}

    /** @param amount 평가손익 고점 대비 감소액(기준 통화, 0 이하) */
    public record MaxDrawdownAmount(BigDecimal amount, LocalDate peak, LocalDate trough) {}

    /** @param pnl 그날 손익(기준 통화, 입출금 제외) */
    public record WorstDayAmount(LocalDate date, BigDecimal pnl) {}

    public record Underwater(LocalDate start, LocalDate end, boolean recovered, long days) {}

    public record WorstDay(LocalDate date, double dailyReturn) {}

    /**
     * 연속 기록. gain·loss는 거래일 단위.
     *
     * @param purchase 적립 연속(주기 단위). 거치식은 null.
     */
    public record StreakSet(StreakStats gain, StreakStats loss, PurchaseStreak purchase) {}

    /** @param longest 없으면 null, current 마지막 날까지 이어지는 연속이 없으면 null */
    public record StreakStats(Streak longest, Streak current) {}

    /** @param unit {@code DAY}(거래일) | {@code WEEK} | {@code MONTH} */
    public record PurchaseStreak(String unit, Streak longest, Streak current) {}

    public record Streak(int length, LocalDate start, LocalDate end) {}

    /**
     * @param close 그날 종가(기준 통화). KRW 기준이면 USD 종목은 그날 USD/KRW(없으면 직전 환율)를 곱한 값,
     *     USD 기준이면 USD 종목은 원래 달러 종가, 국내 종목은 그날 환율로 나눈 값
     * @param purchased 그날 매수가 체결됐는지(순현금흐름 > 0)
     */
    public record Day(
            LocalDate date,
            BigDecimal close,
            BigDecimal value,
            BigDecimal invested,
            double dailyReturn,
            boolean purchased) {}
}
