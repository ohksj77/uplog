package com.uplog.marketdata.seed;

import com.uplog.core.CurrencyCode;
import com.uplog.core.WeekdayTradingCalendar;
import com.uplog.marketdata.DailyPrice;
import com.uplog.marketdata.InstrumentType;
import com.uplog.marketdata.ListedInstrument;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.NewInstrument;
import com.uplog.marketdata.PriceDataVersion;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 기능 B 데모 계좌용 합성 시세: 국내·미국 샘플 종목 8개, 지수 2개(SPX, KOSPI200), USD/KRW 환율.
 * 실제 종목과 헷갈리지 않도록 코드·이름을 샘플로 두고 sample=true로 저장한다. 지수 코드(SPX, KOSPI200)만 벤치마크 이름을 따르지만
 * 값은 합성이다. 가장 최근 평일에서 끝나는 약 3년치 평일 값을 고정 시드로 만든다(매번 같은 모양).
 *
 * <p>종목은 시장 지수에 베타만큼 따라 움직이고, 샘플 ETF 둘은 지수를 거의 그대로 따라간다. 데모 계좌 시드보다 먼저 돈다.
 */
@Component
@Profile("local")
@Order(SampleMarketSeeder.ORDER)
public class SampleMarketSeeder implements ApplicationRunner {

    public static final int ORDER = 100;
    public static final String USD_KRW = "USDKRW";
    /**
     * 합성 환율은 실제 환율(USDKRW)과 섞이지 않게 이 pair로 넣는다. 실제 계좌의 역산은 USDKRW에 빈 날만 채우므로,
     * 합성 값이 USDKRW에 있으면 실제 해외 종목이 가짜 환율로 평가된다. 이 pair는 주인 없는 데모 계좌만 쓴다.
     */
    public static final String SAMPLE_FX_PAIR = "USDKRW_SAMPLE";

    private static final Logger log = LoggerFactory.getLogger(SampleMarketSeeder.class);
    private static final int YEARS = 3;

    /**
     * @param index 따라가는 지수 코드(지수 자신이면 null)
     * @param beta 지수 로그수익률에 곱하는 값 / drift·volatility 종목 고유 일 평균·표준편차 / scale 지수 대비 배율(ETF)
     */
    record Spec(String code, String name, String market, CurrencyCode currency, InstrumentType type, long seed,
            double start, String index, double beta, double drift, double volatility, double scale) {

        int decimals() {
            return currency == CurrencyCode.KRW && type != InstrumentType.INDEX ? 0 : 2;
        }
    }

    static final List<Spec> INDICES = List.of(
            new Spec("KOSPI200", "코스피200", "KR", CurrencyCode.KRW, InstrumentType.INDEX, 9001L, 330, null, 0, 0.00025, 0.011, 0),
            new Spec("SPX", "S&P500", "US", CurrencyCode.USD, InstrumentType.INDEX, 9002L, 4_400, null, 0, 0.00045, 0.010, 0));

    static final List<Spec> INSTRUMENTS = List.of(
            new Spec("S00001", "샘플전자", "KR", CurrencyCode.KRW, InstrumentType.STOCK, 1001L, 62_000, "KOSPI200", 1.2, 0.0002, 0.014, 0),
            new Spec("S00002", "샘플바이오", "KR", CurrencyCode.KRW, InstrumentType.STOCK, 1002L, 85_000, "KOSPI200", 0.8, -0.0001, 0.024, 0),
            new Spec("S00003", "샘플 코스피200 ETF", "KR", CurrencyCode.KRW, InstrumentType.ETF, 1003L, 0, "KOSPI200", 1.0, 0, 0.0007, 100),
            new Spec("S00004", "샘플화학", "KR", CurrencyCode.KRW, InstrumentType.STOCK, 1004L, 310_000, "KOSPI200", 1.0, -0.0002, 0.017, 0),
            new Spec("SMPT", "Sample Tech", "US", CurrencyCode.USD, InstrumentType.STOCK, 2001L, 150, "SPX", 1.3, 0.0003, 0.016, 0),
            new Spec("SMPC", "Sample Chips", "US", CurrencyCode.USD, InstrumentType.STOCK, 2002L, 90, "SPX", 1.6, 0.0002, 0.024, 0),
            new Spec("SMPS", "Sample S&P500 ETF", "US", CurrencyCode.USD, InstrumentType.ETF, 2003L, 0, "SPX", 1.0, 0, 0.0005, 0.012),
            new Spec("SMPE", "Sample Energy", "US", CurrencyCode.USD, InstrumentType.STOCK, 2004L, 70, "SPX", 0.7, 0.0001, 0.015, 0));

    private final MarketDataRepository repository;
    private final PriceDataVersion version;
    private final Clock clock;

    @Autowired
    public SampleMarketSeeder(MarketDataRepository repository, PriceDataVersion version) {
        this(repository, version, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    SampleMarketSeeder(MarketDataRepository repository, PriceDataVersion version, Clock clock) {
        this.repository = repository;
        this.version = version;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate end = SampleEtfSeeder.lastWeekday(LocalDate.now(clock));
        Map<String, List<DailyPrice>> series = generate(end);
        for (Spec spec : allSpecs()) {
            if (repository.findByCode(spec.code()).flatMap(ListedInstrument::range).isPresent()) {
                continue;
            }
            long id = repository.upsertInstrument(
                    new NewInstrument(spec.code(), spec.name(), spec.market(), spec.currency(), spec.type(), true));
            repository.insertDailyPrices(id, series.get(spec.code()));
        }
        if (repository.fxRange(SAMPLE_FX_PAIR).isEmpty()) {
            repository.insertFxRates(SAMPLE_FX_PAIR, series.get(USD_KRW));
        }
        version.bump();
        log.info("샘플 종목·지수 {}종과 {} 환율 시드 완료(~{})", allSpecs().size(), SAMPLE_FX_PAIR, end);
    }

    static List<Spec> allSpecs() {
        List<Spec> all = new ArrayList<>(INDICES);
        all.addAll(INSTRUMENTS);
        return all;
    }

    /** 코드(및 USDKRW) → 평일 종가. 같은 종료일이면 늘 같은 값이다. */
    static Map<String, List<DailyPrice>> generate(LocalDate end) {
        List<LocalDate> days = WeekdayTradingCalendar.weekdaysOnly().tradingDays(end.minusYears(YEARS), end);
        Map<String, double[]> indexReturns = new LinkedHashMap<>();
        Map<String, List<DailyPrice>> result = new LinkedHashMap<>();
        Map<String, double[]> indexLevels = new LinkedHashMap<>();
        for (Spec index : INDICES) {
            Random random = new Random(index.seed());
            double[] returns = new double[days.size()];
            double[] levels = new double[days.size()];
            double level = index.start();
            for (int i = 0; i < days.size(); i++) {
                returns[i] = index.drift() + index.volatility() * random.nextGaussian();
                level *= Math.exp(returns[i]);
                levels[i] = level;
            }
            indexReturns.put(index.code(), returns);
            indexLevels.put(index.code(), levels);
            result.put(index.code(), toPrices(days, levels, index.decimals()));
        }
        for (Spec spec : INSTRUMENTS) {
            Random random = new Random(spec.seed());
            double[] market = indexReturns.get(spec.index());
            double[] levels = new double[days.size()];
            double price = spec.start();
            for (int i = 0; i < days.size(); i++) {
                double noise = spec.volatility() * random.nextGaussian();
                if (spec.scale() > 0) {
                    // ETF: 지수 × 배율에 아주 작은 추적 오차
                    levels[i] = indexLevels.get(spec.index())[i] * spec.scale() * Math.exp(noise);
                } else {
                    price *= Math.exp(spec.drift() + spec.beta() * market[i] + noise);
                    levels[i] = price;
                }
            }
            result.put(spec.code(), toPrices(days, levels, spec.decimals()));
        }
        result.put(USD_KRW, toPrices(days, usdKrw(days.size()), 2));
        return result;
    }

    /** 1,360원 근처로 되돌아가는 로그 랜덤워크. */
    private static double[] usdKrw(int size) {
        Random random = new Random(7001L);
        double[] rates = new double[size];
        double log = Math.log(1_300);
        double mean = Math.log(1_360);
        for (int i = 0; i < size; i++) {
            log += 0.01 * (mean - log) + 0.0045 * random.nextGaussian();
            rates[i] = Math.exp(log);
        }
        return rates;
    }

    private static List<DailyPrice> toPrices(List<LocalDate> days, double[] levels, int decimals) {
        List<DailyPrice> prices = new ArrayList<>(days.size());
        for (int i = 0; i < days.size(); i++) {
            prices.add(DailyPrice.close(days.get(i), BigDecimal.valueOf(levels[i]).setScale(decimals, RoundingMode.HALF_UP)));
        }
        return prices;
    }
}
