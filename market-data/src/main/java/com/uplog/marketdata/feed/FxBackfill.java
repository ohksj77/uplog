package com.uplog.marketdata.feed;

import com.uplog.calc.PriceSeries;
import com.uplog.marketdata.DailyPrice;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.PriceDataVersion;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 원화 환산에 쓸 실제 USD/KRW(fx_rate의 {@value #PAIR})를 필요할 때만 받는다. 일별 환율은 보통 수집 작업이 미리 채워 두고,
 * 여기서는 꼭 필요한 날(매수일과 종료일) 중 가까운(7일 안) 환율이 없는 날만 받는다. 받는 날 수는 {@value #MAX_FETCH}일로
 * 묶는다(넘으면 달마다 첫 날과 마지막 날만).
 */
@Component
public class FxBackfill {

    public static final String PAIR = "USDKRW";
    static final int MAX_FETCH = 40;
    static final int NEAR_DAYS = 7;

    private final MarketDataRepository marketData;
    private final PriceDataVersion version;

    public FxBackfill(MarketDataRepository marketData, PriceDataVersion version) {
        this.marketData = marketData;
        this.version = version;
    }

    /** @return 새로 넣은 날 수 */
    public int ensure(MarketFeed feed, Collection<LocalDate> needed) {
        if (needed.isEmpty()) {
            return 0;
        }
        TreeSet<LocalDate> dates = new TreeSet<>(needed);
        NavigableMap<LocalDate, BigDecimal> have = marketData
                .loadFxRates(PAIR, dates.first().minusDays(NEAR_DAYS), dates.last())
                .map(s -> s.range(dates.first().minusDays(NEAR_DAYS), dates.last()))
                .orElse(new java.util.TreeMap<>());
        List<LocalDate> missing = new ArrayList<>();
        for (LocalDate d : dates) {
            LocalDate near = have.floorKey(d);
            if (near == null || near.isBefore(d.minusDays(NEAR_DAYS))) {
                missing.add(d);
            }
        }
        int added = 0;
        for (LocalDate d : bounded(missing)) {
            Optional<BigDecimal> rate = feed.usdKrw(d);
            if (rate.isPresent() && rate.get().signum() > 0) {
                try {
                    marketData.insertFxRates(PAIR, List.of(DailyPrice.close(d, rate.get())));
                    added++;
                } catch (DuplicateKeyException e) {
                    // 수집 작업이나 다른 요청이 먼저 넣었다
                }
            }
        }
        if (added > 0) {
            version.bump();
        }
        return added;
    }

    /** 너무 많으면 달마다 첫 날만 남기고 마지막 날은 꼭 넣는다. 그래도 많으면 고르게 솎는다. */
    static List<LocalDate> bounded(List<LocalDate> missing) {
        if (missing.size() <= MAX_FETCH) {
            return missing;
        }
        Map<YearMonth, LocalDate> firstOfMonth = new LinkedHashMap<>();
        for (LocalDate d : missing) {
            firstOfMonth.putIfAbsent(YearMonth.from(d), d);
        }
        TreeSet<LocalDate> picked = new TreeSet<>(firstOfMonth.values());
        picked.add(missing.getLast());
        List<LocalDate> list = new ArrayList<>(picked);
        if (list.size() <= MAX_FETCH) {
            return list;
        }
        List<LocalDate> thinned = new ArrayList<>();
        for (int i = 0; i < MAX_FETCH - 1; i++) {
            thinned.add(list.get((int) ((long) i * (list.size() - 1) / (MAX_FETCH - 1))));
        }
        thinned.add(list.getLast());
        return thinned;
    }

    /**
     * 현지 종가를 원화로 바꾼다. 그날 환율이 없으면 직전 환율, 구간 앞에 환율이 없으면 첫 환율을 쓴다.
     *
     * @return 환율이 하나도 없으면 empty
     */
    public static Optional<PriceSeries> toKrw(PriceSeries closes, Optional<PriceSeries> fx) {
        if (fx.isEmpty()) {
            return Optional.empty();
        }
        PriceSeries rates = fx.get();
        Map<LocalDate, BigDecimal> krw = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, BigDecimal> e : closes.range(closes.firstDate(), closes.lastDate()).entrySet()) {
            BigDecimal rate = rates.closeOnOrBefore(e.getKey()).orElseGet(() -> rates.closeOn(rates.firstDate()));
            krw.put(e.getKey(), e.getValue().multiply(rate));
        }
        return Optional.of(new PriceSeries(krw));
    }
}
