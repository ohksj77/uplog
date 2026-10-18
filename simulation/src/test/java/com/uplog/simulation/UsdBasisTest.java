package com.uplog.simulation;

import static com.uplog.simulation.Prices.MON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.uplog.calc.BenchmarkReplay;
import com.uplog.calc.CashFlow;
import com.uplog.calc.DailyReturn;
import com.uplog.calc.PriceSeries;
import com.uplog.calc.ReplayPoint;
import com.uplog.calc.Twr;
import com.uplog.core.CurrencyCode;
import com.uplog.marketdata.feed.FxBackfill;
import com.uplog.simulation.api.SimulationResponse;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 달러 기준: 같은 매수를 그날 환율로 달러로 나타내 다시 계산한다. */
class UsdBasisTest {

    // 평일 15일(3주). 미국 종목 달러 종가와 원/달러 환율(주말 없이 매 거래일 있음)
    private static final double[] SPY_USD = {500, 505, 498, 510, 515, 520, 512, 508, 530, 540, 535, 545, 550, 548, 560};
    private static final double[] USDKRW = {1528, 1520, 1515, 1500, 1490, 1480, 1460, 1450, 1430, 1420, 1400, 1380, 1370, 1355, 1343};

    private static final SimulationQuery WEEKLY = new SimulationQuery(
            List.of("SPY"), SimulationPeriod.SIX_MONTHS, RecurringSchedule.weekly(DayOfWeek.MONDAY),
            AmountType.PER_PURCHASE, 300_000, CurrencyCode.USD);

    private record Run(SimulationCalculator.Pair krw, SimulationCalculator.Pair usd, PriceSeries fx, PriceSeries usdCloses) {}

    /** 미국 종목: 원화 환산 종가에 원화로 사고, 달러 기준이면 원래 달러 종가·같은 환율로 나타낸다(서비스와 같은 흐름). */
    private static Run usInstrument(SimulationQuery query, double[] usdCloses, double[] fx) {
        PriceSeries local = Prices.weekdays(MON, usdCloses);
        PriceSeries rates = Prices.weekdays(MON, fx);
        PriceSeries krw = FxBackfill.toKrw(local, Optional.of(rates)).orElseThrow();
        LocalDate to = krw.lastDate();
        return new Run(
                SimulationCalculator.calculate(query, krw, MON, to),
                SimulationCalculator.calculate(query, krw, MON, to, Optional.of(new UsdBasis(rates, Optional.of(local)))),
                rates,
                local);
    }

    @Test
    void 달러_원금은_매수일_원화_금액을_그날_환율로_나눈_합이다() {
        Run run = usInstrument(WEEKLY, SPY_USD, USDKRW);

        BigDecimal expected = BigDecimal.ZERO;
        for (SimulationResponse.Day day : run.krw().recurring().days()) {
            if (day.purchased()) {
                expected = expected.add(BigDecimal.valueOf(300_000).divide(run.fx().closeOn(day.date()), MathContext.DECIMAL64));
            }
        }
        SimulationResponse.Summary usd = run.usd().recurring().summary();
        assertThat(usd.invested()).isEqualByComparingTo(expected.setScale(2, java.math.RoundingMode.HALF_UP));
        assertThat(usd.invested().scale()).isEqualTo(2);
        assertThat(usd.profit()).isEqualTo(usd.finalValue().subtract(usd.invested()));
        // 원화 기준 응답은 그대로 원 단위 정수
        assertThat(run.krw().recurring().summary().invested()).isEqualTo(BigDecimal.valueOf(900_000));
        assertThat(run.krw().recurring().summary().invested().scale()).isZero();
    }

    @Test
    void 날마다_달러_평가금액에_그날_환율을_곱하면_원화_평가금액이다() {
        Run run = usInstrument(WEEKLY, SPY_USD, USDKRW);

        for (SimulationCalculator.Pair pair : List.of(run.krw(), run.usd())) {
            assertThat(pair.lumpSum().days()).hasSize(15);
        }
        for (int k = 0; k < 2; k++) {
            List<SimulationResponse.Day> krwDays = k == 0 ? run.krw().lumpSum().days() : run.krw().recurring().days();
            List<SimulationResponse.Day> usdDays = k == 0 ? run.usd().lumpSum().days() : run.usd().recurring().days();
            assertThat(usdDays).hasSameSizeAs(krwDays);
            for (int i = 0; i < krwDays.size(); i++) {
                SimulationResponse.Day krw = krwDays.get(i);
                SimulationResponse.Day usd = usdDays.get(i);
                BigDecimal rate = run.fx().closeOn(krw.date());
                assertThat(usd.date()).isEqualTo(krw.date());
                assertThat(usd.purchased()).isEqualTo(krw.purchased());
                // 센트 반올림(±0.005달러 × 환율) + 원 반올림 안
                assertThat(usd.value().multiply(rate).doubleValue()).isCloseTo(krw.value().doubleValue(), within(10.0));
                // 미국 종목 종가는 원래 달러 종가
                assertThat(usd.close()).isEqualByComparingTo(run.usdCloses().closeOn(krw.date()));
            }
        }
    }

    @Test
    void 미국_ETF의_달러_기준_결과는_달러_종가로_같은_달러_금액을_산_적립식과_같다() {
        Run run = usInstrument(WEEKLY, SPY_USD, USDKRW);

        // 같은 날 300,000원 / 그날 환율만큼의 달러를 달러 종가에 산다
        List<CashFlow> flows = new ArrayList<>();
        for (SimulationResponse.Day day : run.krw().recurring().days()) {
            if (day.purchased()) {
                flows.add(new CashFlow(day.date(),
                        BigDecimal.valueOf(300_000).divide(run.fx().closeOn(day.date()), MathContext.DECIMAL64)));
            }
        }
        List<ReplayPoint> points = BenchmarkReplay.replay(flows, run.usdCloses(), run.usdCloses().lastDate());
        List<DailyReturn> returns = Twr.compute(points.stream().map(ReplayPoint::toValuation).toList());
        ReplayPoint last = points.getLast();
        double totalReturn = last.value().divide(last.invested(), MathContext.DECIMAL64).doubleValue() - 1;

        SimulationResponse.Summary usd = run.usd().recurring().summary();
        assertThat(usd.totalReturn()).isCloseTo(totalReturn, within(1e-12));
        assertThat(usd.twr()).isCloseTo(returns.getLast().twrIndex() - 1, within(1e-12));
        assertThat(usd.finalValue()).isEqualByComparingTo(last.value().setScale(2, java.math.RoundingMode.HALF_UP));

        // 거치식은 달러 가격 수익률 그대로
        double priceReturn = SPY_USD[SPY_USD.length - 1] / SPY_USD[0] - 1;
        assertThat(run.usd().lumpSum().summary().totalReturn()).isCloseTo(priceReturn, within(1e-12));
        assertThat(run.usd().lumpSum().summary().twr()).isCloseTo(priceReturn, within(1e-12));
        // 같은 기간 원화가 강해져 원화 기준 수익은 더 작다
        assertThat(run.krw().lumpSum().summary().totalReturn()).isLessThan(priceReturn);
    }

    @Test
    void 달러_가격이_그대로이고_원화가_강해지면_원화_기준은_손실_달러_기준은_0이다() {
        double[] flat = new double[15];
        java.util.Arrays.fill(flat, 100);
        Run run = usInstrument(WEEKLY, flat, USDKRW);

        for (SimulationCalculator.Pair krw : List.of(run.krw())) {
            assertThat(krw.lumpSum().summary().totalReturn()).isCloseTo(1343.0 / 1528 - 1, within(1e-12));
            assertThat(krw.recurring().summary().totalReturn()).isNegative();
            assertThat(krw.recurring().summary().profit().signum()).isNegative();
        }
        for (SimulationResponse.Result usd : List.of(run.usd().lumpSum(), run.usd().recurring())) {
            assertThat(usd.summary().totalReturn()).isCloseTo(0, within(1e-12));
            assertThat(usd.summary().twr()).isCloseTo(0, within(1e-12));
            assertThat(usd.summary().profit()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(usd.pain().maxDrawdown().rate()).isCloseTo(0, within(1e-12));
            assertThat(usd.pain().underwater()).isNull();
            assertThat(usd.streaks().loss().longest()).isNull();
        }
    }

    @Test
    void 국내_종목도_달러_기준이면_원화_종가를_그날_환율로_나눈다() {
        PriceSeries krw = Prices.weekdays(MON, 41_250, 41_500, 41_000, 42_000, 42_500);
        PriceSeries rates = Prices.weekdays(MON, 1500, 1490, 1480, 1470, 1460);
        SimulationQuery query = new SimulationQuery(List.of("069500"), SimulationPeriod.SIX_MONTHS,
                RecurringSchedule.daily(), AmountType.PER_PURCHASE, 10_000, CurrencyCode.USD);

        SimulationCalculator.Pair usd = SimulationCalculator.calculate(
                query, krw, MON, krw.lastDate(), Optional.of(new UsdBasis(rates, Optional.empty())));

        SimulationResponse.Day first = usd.recurring().days().getFirst();
        assertThat(first.close()).isEqualByComparingTo("27.50"); // 41,250 / 1,500
        assertThat(first.invested()).isEqualByComparingTo("6.67"); // 10,000 / 1,500
        assertThat(usd.recurring().days().getLast().close()).isEqualByComparingTo("29.11"); // 42,500 / 1,460
    }

    @Test
    void 환율이_없는_날은_직전_환율_첫날_앞이면_첫_환율을_쓴다() {
        PriceSeries rates = Prices.weekdays(MON.plusDays(1), 1400, 1300); // 화·수
        UsdBasis basis = new UsdBasis(rates, Optional.empty());

        assertThat(basis.rate(MON)).isEqualByComparingTo("1400");
        assertThat(basis.rate(MON.plusDays(3))).isEqualByComparingTo("1300");
    }

    @Test
    void 응답_JSON의_금액은_원화면_정수_달러면_소수_둘째_자리다() {
        Run run = usInstrument(WEEKLY, SPY_USD, USDKRW);
        JsonMapper json = JsonMapper.builder().build();

        String krwBody = json.writeValueAsString(response(run.krw(), "KRW"));
        String usdBody = json.writeValueAsString(response(run.usd(), "USD"));
        JsonNode krw = json.readTree(krwBody);
        JsonNode usd = json.readTree(usdBody);

        assertThat(krw.get("currency").asString()).isEqualTo("KRW");
        JsonNode krwSummary = krw.get("results").get(0).get("recurring").get("summary");
        assertThat(krwSummary.get("invested").isIntegralNumber()).isTrue();
        assertThat(krw.get("results").get(0).get("recurring").get("days").get(0).get("close").isIntegralNumber()).isTrue();
        assertThat(krwBody).contains("\"invested\":900000,");

        assertThat(usd.get("currency").asString()).isEqualTo("USD");
        assertThat(usd.get("results").get(0).get("recurring").get("summary").get("invested").isIntegralNumber()).isFalse();
        assertThat(usdBody).containsPattern("\"summary\":\\{\"finalValue\":\\d+\\.\\d{2},\"invested\":\\d+\\.\\d{2},");
        assertThat(usdBody).containsPattern("\"close\":\\d+\\.\\d{2},");
        System.out.println(usdBody);
    }

    private static SimulationResponse response(SimulationCalculator.Pair pair, String currency) {
        return new SimulationResponse(MON, MON.plusWeeks(3), currency, List.of(new SimulationResponse.EtfResult(
                new SimulationResponse.Etf("SPY", "SPDR S&P 500 ETF", "US", "ETF", "USD"), pair.lumpSum(), pair.recurring())),
                SimulationResponse.DISCLAIMER);
    }
}
