package com.uplog.simulation;

import com.uplog.calc.DailyReturn;
import com.uplog.calc.PainMetrics;
import com.uplog.calc.PerformanceSummary;
import com.uplog.calc.PriceSeries;
import com.uplog.calc.ReplayPoint;
import com.uplog.calc.Simulation;
import com.uplog.calc.Twr;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 달러 기준 보기. 매수(원화 금액·날짜, 그래서 수량)는 원화 기준과 같고, 그날 USD/KRW로 나눠 달러로 나타낸 뒤 수익률·고통 지표를
 * 달러 시계열로 다시 계산한다. 환율 변동 효과가 빠진 결과다.
 *
 * <ul>
 *   <li>현금흐름: {@code cf_usd = cf_krw / fx(매수일)}, 원금은 그 합</li>
 *   <li>평가금액: {@code value_usd = value_krw / fx(그날)} (미국 종목이면 수량 × 달러 종가와 같다)</li>
 *   <li>종가: 미국 종목은 원래 달러 종가, 국내 종목은 {@code close_krw / fx(그날)}</li>
 * </ul>
 *
 * <p>환율은 원화 환산({@link com.uplog.marketdata.feed.FxBackfill#toKrw})과 같은 규칙: 그날 환율이 없으면 직전 환율,
 * 구간 앞에 환율이 없으면 첫 환율.
 *
 * @param fx USDKRW 환율
 * @param usdCloses 미국 종목의 원래 달러 종가. 국내 종목이면 empty.
 */
public record UsdBasis(PriceSeries fx, Optional<PriceSeries> usdCloses) {

    public UsdBasis {
        Objects.requireNonNull(fx, "fx");
        Objects.requireNonNull(usdCloses, "usdCloses");
    }

    BigDecimal rate(LocalDate date) {
        return fx.closeOnOrBefore(date).orElseGet(() -> fx.closeOn(fx.firstDate()));
    }

    /** 원화 기준 결과를 같은 매수 그대로 달러 기준으로 바꾼다. */
    Simulation convert(Simulation krw) {
        List<ReplayPoint> points = new ArrayList<>(krw.points().size());
        BigDecimal invested = BigDecimal.ZERO;
        for (ReplayPoint p : krw.points()) {
            BigDecimal rate = rate(p.date());
            BigDecimal cashFlow = p.netCashFlow().signum() == 0
                    ? BigDecimal.ZERO
                    : p.netCashFlow().divide(rate, MathContext.DECIMAL64);
            invested = invested.add(cashFlow);
            Optional<BigDecimal> usdClose = usdCloses.map(s -> s.closeOn(p.date()));
            BigDecimal close = usdClose.orElseGet(() -> p.close().divide(rate, MathContext.DECIMAL64));
            // 미국 종목은 원래 달러 종가로 평가해 환산 반올림이 끼지 않게 한다(값은 value_krw / fx와 같다)
            BigDecimal value = usdClose.isPresent()
                    ? p.units().multiply(close)
                    : p.value().divide(rate, MathContext.DECIMAL64);
            points.add(new ReplayPoint(p.date(), close, p.units(), value, invested, cashFlow));
        }
        List<ReplayPoint> usd = List.copyOf(points);
        List<DailyReturn> returns = Twr.compute(usd.stream().map(ReplayPoint::toValuation).toList());
        return new Simulation(usd, returns, PerformanceSummary.of(usd, returns), PainMetrics.of(usd, returns));
    }
}
