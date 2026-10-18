package com.uplog.simulation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uplog.calc.PriceSeries;
import com.uplog.core.CurrencyCode;
import com.uplog.marketdata.Instrument;
import com.uplog.marketdata.InstrumentType;
import com.uplog.marketdata.ListedInstrument;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.PriceDataVersion;
import com.uplog.marketdata.PriceRange;
import com.uplog.simulation.api.EtfResponse;
import com.uplog.simulation.api.SimulationResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 기능 A 조회. 결과는 (정규화한 요청, 종목별 마지막 시세일, 시세 버전)을 키로 캐시한다.
 * 새 시세가 들어오면 마지막 시세일이나 버전이 바뀌어 자연히 새 키가 된다.
 */
@Service
public class SimulationService {

    public static final int DEFAULT_ETF_LIMIT = 20;
    public static final int MAX_ETF_LIMIT = 100;

    private record CacheKey(SimulationQuery query, List<LocalDate> lastDates, long dataVersion) {}

    private record Selected(Instrument instrument, PriceRange range) {}

    private final MarketDataRepository repository;
    private final PriceDataVersion dataVersion;
    private final Cache<CacheKey, SimulationResponse> cache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofHours(12))
            .build();

    public SimulationService(MarketDataRepository repository, PriceDataVersion dataVersion) {
        this.repository = repository;
        this.dataVersion = dataVersion;
    }

    public List<EtfResponse> searchEtfs(String query, int limit) {
        if (limit < 1 || limit > MAX_ETF_LIMIT) {
            throw SimulationException.invalid("limit은 1–" + MAX_ETF_LIMIT + " 사이여야 합니다.");
        }
        return repository.searchEtfs(query, limit).stream()
                .map(l -> {
                    Instrument i = l.instrument();
                    PriceRange r = l.range().orElseThrow();
                    return new EtfResponse(i.code(), i.name(), i.currency().name(), i.sample(), r.firstDate(), r.lastDate());
                })
                .toList();
    }

    public SimulationResponse simulate(SimulationQuery query) {
        List<Selected> selected = new ArrayList<>();
        for (String code : query.etfs()) {
            ListedInstrument listed = repository.findByCode(code)
                    .filter(l -> l.instrument().type() == InstrumentType.ETF && l.instrument().currency() == CurrencyCode.KRW)
                    .orElseThrow(() -> new SimulationException(ErrorCode.ETF_NOT_FOUND, "ETF를 찾을 수 없습니다: " + code));
            PriceRange range = listed.range()
                    .orElseThrow(() -> new SimulationException(
                            ErrorCode.NO_PRICE_DATA, listed.instrument().name() + "의 시세가 아직 없습니다."));
            selected.add(new Selected(listed.instrument(), range));
        }
        List<LocalDate> lastDates = selected.stream().map(s -> s.range().lastDate()).toList();
        CacheKey key = new CacheKey(query, lastDates, dataVersion.current());
        return cache.get(key, k -> compute(query, selected));
    }

    private SimulationResponse compute(SimulationQuery query, List<Selected> selected) {
        // 여러 ETF를 같은 구간으로 비교하도록 종료일을 마지막 시세일 중 가장 이른 날로 맞춘다.
        LocalDate to = selected.stream().map(s -> s.range().lastDate()).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate from = query.period().startFrom(to);
        List<SimulationResponse.EtfResult> results = new ArrayList<>();
        for (Selected s : selected) {
            Instrument i = s.instrument();
            PriceSeries prices = repository.loadCloses(i.id(), from, to)
                    .orElseThrow(() -> new SimulationException(
                            ErrorCode.NO_PRICE_DATA, i.name() + "의 " + from + " ~ " + to + " 시세가 없습니다."));
            SimulationCalculator.Pair pair = SimulationCalculator.calculate(query, prices, from, to);
            results.add(new SimulationResponse.EtfResult(
                    new SimulationResponse.Etf(i.code(), i.name(), i.currency().name(), i.sample()),
                    pair.lumpSum(),
                    pair.recurring()));
        }
        return new SimulationResponse(from, to, results, SimulationResponse.DISCLAIMER);
    }
}
