package com.uplog.marketdata.seed;

import com.uplog.core.CurrencyCode;
import com.uplog.core.TradingCalendar;
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
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬 개발용 합성 ETF 4종(SAMPLE01–04). 실제 종목과 헷갈리지 않도록 코드·이름을 "샘플"로 두고 sample=true로 저장한다.
 * 시세는 고정 시드 랜덤워크라 매번 같은 모양이고, 가장 최근 평일에서 끝나는 약 4년치 평일 종가다.
 */
@Component
@Profile("local")
public class SampleEtfSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleEtfSeeder.class);
    private static final int YEARS = 4;

    /** @param drift 일 평균 로그수익률, volatility 일 표준편차 */
    record SampleEtf(String code, String name, long seed, double start, double drift, double volatility) {}

    static final List<SampleEtf> SAMPLES = List.of(
            new SampleEtf("SAMPLE01", "샘플 국내 대형주 ETF", 101L, 10_000, 0.0003, 0.011),
            new SampleEtf("SAMPLE02", "샘플 미국 기술주 ETF", 202L, 15_000, 0.0005, 0.017),
            new SampleEtf("SAMPLE03", "샘플 고배당 ETF", 303L, 8_000, 0.0001, 0.008),
            new SampleEtf("SAMPLE04", "샘플 2차전지 테마 ETF", 404L, 12_000, -0.0001, 0.025));

    private final MarketDataRepository repository;
    private final PriceDataVersion version;
    private final Clock clock;

    @Autowired
    public SampleEtfSeeder(MarketDataRepository repository, PriceDataVersion version) {
        this(repository, version, Clock.system(ZoneId.of("Asia/Seoul")));
    }

    SampleEtfSeeder(MarketDataRepository repository, PriceDataVersion version, Clock clock) {
        this.repository = repository;
        this.version = version;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        LocalDate end = lastWeekday(LocalDate.now(clock));
        for (SampleEtf etf : SAMPLES) {
            if (repository.findByCode(etf.code()).flatMap(ListedInstrument::range).isPresent()) {
                continue;
            }
            long id = repository.upsertInstrument(
                    new NewInstrument(etf.code(), etf.name(), "SAMPLE", CurrencyCode.KRW, InstrumentType.ETF, true));
            repository.insertDailyPrices(id, prices(etf, end));
        }
        version.bump();
        log.info("샘플 ETF {}종 시드 완료(~{})", SAMPLES.size(), end);
    }

    static LocalDate lastWeekday(LocalDate date) {
        TradingCalendar calendar = WeekdayTradingCalendar.weekdaysOnly();
        return calendar.isTradingDay(date) ? date : calendar.previous(date);
    }

    static List<DailyPrice> prices(SampleEtf etf, LocalDate end) {
        List<LocalDate> days = WeekdayTradingCalendar.weekdaysOnly().tradingDays(end.minusYears(YEARS), end);
        Random random = new Random(etf.seed());
        List<DailyPrice> prices = new ArrayList<>(days.size());
        double price = etf.start();
        for (LocalDate day : days) {
            price = Math.max(100, price * Math.exp(etf.drift() + etf.volatility() * random.nextGaussian()));
            prices.add(DailyPrice.close(day, BigDecimal.valueOf(price).setScale(0, RoundingMode.HALF_UP)));
        }
        return prices;
    }
}
