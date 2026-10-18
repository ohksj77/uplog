package com.uplog.portfolio.demo;

import com.uplog.calc.PriceSeries;
import com.uplog.marketdata.ListedInstrument;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.PriceRange;
import com.uplog.portfolio.PortfolioDataVersion;
import com.uplog.portfolio.PortfolioRepository;
import com.uplog.portfolio.PortfolioService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * desktop 프로필의 합성 데모 계좌("데모 계좌", sample=true). 샘플 시세(SampleMarketSeeder)가 들어간 뒤에 돈다.
 * 토스 수집기가 없어 이 원시 데이터로 기능 B 화면을 띄운다. 데모 계좌가 이미 있으면 건너뛴다.
 * 주인이 없는 계좌(user_id = null)라서 토스를 연결하기 전에 보인다.
 */
@Component
@Profile("desktop")
@Order(200)
public class DemoAccountSeeder implements ApplicationRunner {

    static final List<String> CODES = List.of("S00001", "S00002", "S00003", "S00004", "SMPT", "SMPC", "SMPS", "SMPE");

    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeeder.class);

    private final MarketDataRepository marketData;
    private final PortfolioRepository repository;
    private final PortfolioDataVersion version;
    private final PortfolioService service;

    public DemoAccountSeeder(
            MarketDataRepository marketData, PortfolioRepository repository, PortfolioDataVersion version,
            PortfolioService service) {
        this.marketData = marketData;
        this.repository = repository;
        this.version = version;
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.demoAccount().isPresent()) {
            return;
        }
        long accountId = seed();
        version.bump();
        service.refresh(accountId);
    }

    long seed() {
        Map<String, PriceSeries> closes = new TreeMap<>();
        LocalDate end = null;
        for (String code : CODES) {
            ListedInstrument listed = marketData.findByCode(code)
                    .orElseThrow(() -> new IllegalStateException("샘플 시세가 없습니다: " + code));
            PriceRange range = listed.range().orElseThrow();
            closes.put(code, marketData.loadCloses(listed.instrument().id(), range.firstDate(), range.lastDate()).orElseThrow());
            end = end == null || range.lastDate().isBefore(end) ? range.lastDate() : end;
        }
        PriceSeries usdKrw = marketData.loadFxRates(com.uplog.marketdata.seed.SampleMarketSeeder.SAMPLE_FX_PAIR, end.minusYears(3), end)
                .orElseThrow(() -> new IllegalStateException("샘플 환율이 없습니다"));
        DemoAccountScript.Plan plan = DemoAccountScript.generate(closes, usdKrw, end);

        long accountId = repository.insertAccount(DemoAccountScript.NAME, "TOSS", true, plan.connectedAt());
        repository.insertCashFlows(accountId, plan.flows(), "demo-f-");
        repository.insertTrades(accountId, plan.trades(), "demo-t-");
        repository.insertDividends(accountId, plan.dividends(), "demo-d-");
        for (LocalDate gap : plan.gaps()) {
            repository.insertGap(accountId, gap, "데모: 수집 실패 예시");
        }
        log.info("데모 계좌 시드 완료: 체결 {}건, 입출금 {}건, 배당 {}건, 연결일 {}, 수집 실패 {}",
                plan.trades().size(), plan.flows().size(), plan.dividends().size(), plan.connectedAt(), plan.gaps());
        return accountId;
    }
}
