package com.uplog.simulation;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uplog.calc.CashFlow;
import com.uplog.calc.PriceSeries;
import com.uplog.core.CurrencyCode;
import com.uplog.marketdata.Instrument;
import com.uplog.marketdata.InstrumentType;
import com.uplog.marketdata.ListedInstrument;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.PriceDataVersion;
import com.uplog.marketdata.PriceRange;
import com.uplog.marketdata.feed.CandleBackfill;
import com.uplog.marketdata.feed.FeedRepository;
import com.uplog.marketdata.feed.FxBackfill;
import com.uplog.marketdata.feed.InstrumentUniverse;
import com.uplog.marketdata.feed.MarketFeed;
import com.uplog.simulation.api.EtfResponse;
import com.uplog.simulation.api.SimulationResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * 기능 A 조회. 결과는 (정규화한 요청(기준 통화 포함), 종목별 마지막 시세일, 시세 버전)을 키로 캐시한다.
 * 새 시세가 들어오면 마지막 시세일이나 버전이 바뀌어 자연히 새 키가 된다.
 *
 * <p>실제 종목(국내·미국 ETF·주식)만 다룬다. 시세 소스({@link MarketFeed})는 요청자 자신의 키로 만든 것만 받고, 없으면
 * (비로그인·미연결) 검색·시뮬레이션 모두 SIMULATION_REQUIRES_CONNECTION이다. 모자란 일봉·환율은 그 키로 받고, 받지 못하면
 * {@link com.uplog.marketdata.feed.MarketFeedException}이 그대로 올라가 MARKET_DATA_UNAVAILABLE이 된다(다른 데이터로 대신하지 않음).
 * 샘플 종목(데모 계좌용 합성 시세, sample=true)은 검색에도 나오지 않고 요청해도 없는 종목으로 본다.
 */
@Service
public class SimulationService {

    public static final int DEFAULT_ETF_LIMIT = 20;
    public static final int MAX_ETF_LIMIT = 100;
    /** 실제 종목 일봉을 받을 때 기간 앞에 더 받는 날(종료일이 며칠 앞당겨져도 기간을 채우도록) */
    static final int LOOKBACK_BUFFER_DAYS = 10;
    /** 환율은 기간 시작 전 이만큼까지 읽어 첫날 환율을 정한다 */
    static final int FX_LOOKBACK_DAYS = 30;

    private record CacheKey(SimulationQuery query, List<LocalDate> lastDates, long dataVersion) {}

    private record Selected(Instrument instrument, PriceRange range) {}

    private final MarketDataRepository repository;
    private final PriceDataVersion dataVersion;
    private final FeedRepository feedRepository;
    private final InstrumentUniverse universe;
    private final CandleBackfill candles;
    private final FxBackfill fx;
    private final Cache<CacheKey, SimulationResponse> cache = Caffeine.newBuilder()
            .maximumSize(1_000)
            .expireAfterWrite(Duration.ofHours(12))
            .build();

    public SimulationService(
            MarketDataRepository repository,
            PriceDataVersion dataVersion,
            FeedRepository feedRepository,
            InstrumentUniverse universe,
            CandleBackfill candles,
            FxBackfill fx) {
        this.repository = repository;
        this.dataVersion = dataVersion;
        this.feedRepository = feedRepository;
        this.universe = universe;
        this.candles = candles;
        this.fx = fx;
    }

    /** 실제 ETF·주식(정확한 코드 → ETF → 주식 순). 시세 소스가 없으면 SIMULATION_REQUIRES_CONNECTION. */
    public List<EtfResponse> searchInstruments(Optional<MarketFeed> feed, String query, int limit) {
        if (limit < 1 || limit > MAX_ETF_LIMIT) {
            throw SimulationException.invalid("limit은 1–" + MAX_ETF_LIMIT + " 사이여야 합니다.");
        }
        MarketFeed source = feed.orElseThrow(SimulationException::requiresConnection);
        universe.ensureFresh(source);
        return feedRepository.searchRealInstruments(query, limit).stream()
                .map(l -> {
                    Instrument i = l.instrument();
                    return new EtfResponse(i.code(), i.name(), InstrumentUniverse.country(i.currency()), i.type().name(),
                            i.currency().name(),
                            l.range().map(PriceRange::firstDate).orElse(null),
                            l.range().map(PriceRange::lastDate).orElse(null));
                })
                .toList();
    }

    public SimulationResponse simulate(Optional<MarketFeed> feed, SimulationQuery query) {
        MarketFeed source = feed.orElseThrow(SimulationException::requiresConnection);
        List<Selected> selected = new ArrayList<>();
        for (String code : query.etfs()) {
            Instrument instrument = resolve(source, code);
            LocalDate need = query.period().startFrom(candles.lastCompleteDay(instrument.currency()))
                    .minusDays(LOOKBACK_BUFFER_DAYS);
            if (!candles.ensure(source, instrument, need)) {
                throw new SimulationException(
                        ErrorCode.NO_PRICE_DATA, instrument.name() + "의 시세를 증권사에서 받을 수 없습니다(상장폐지 등).");
            }
            PriceRange range = repository.priceRange(instrument.id())
                    .orElseThrow(() -> new SimulationException(ErrorCode.NO_PRICE_DATA, instrument.name() + "의 시세가 아직 없습니다."));
            selected.add(new Selected(instrument, range));
        }
        List<LocalDate> lastDates = selected.stream().map(s -> s.range().lastDate()).toList();
        CacheKey key = new CacheKey(query, lastDates, dataVersion.current());
        SimulationResponse cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        SimulationResponse computed = compute(source, query, selected);
        cache.put(key, computed);
        return computed;
    }

    /**
     * 저장된 종목을 찾고, 없으면 증권사 상세로 확인해 넣는다. 샘플 종목, 지수 등 ETF·주식이 아닌 것은 없는 종목으로 본다.
     */
    private Instrument resolve(MarketFeed feed, String code) {
        return universe.findOrFetch(feed, code)
                .map(ListedInstrument::instrument)
                .filter(i -> !i.sample())
                .filter(i -> i.type() == InstrumentType.ETF || i.type() == InstrumentType.STOCK)
                .orElseThrow(() -> new SimulationException(ErrorCode.ETF_NOT_FOUND, "종목을 찾을 수 없습니다: " + code));
    }

    private SimulationResponse compute(MarketFeed feed, SimulationQuery query, List<Selected> selected) {
        // 여러 종목을 같은 구간으로 비교하도록 종료일을 마지막 시세일 중 가장 이른 날로 맞춘다.
        LocalDate to = selected.stream().map(s -> s.range().lastDate()).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate from = query.period().startFrom(to);
        boolean usdBasis = query.currency() == CurrencyCode.USD;
        List<SimulationResponse.EtfResult> results = new ArrayList<>();
        for (Selected s : selected) {
            Instrument i = s.instrument();
            PriceSeries local = repository.loadCloses(i.id(), from, to)
                    .orElseThrow(() -> new SimulationException(
                            ErrorCode.NO_PRICE_DATA, i.name() + "의 " + from + " ~ " + to + " 시세가 없습니다."));
            boolean usdInstrument = i.currency() == CurrencyCode.USD;
            Optional<PriceSeries> fxRates = usdInstrument || usdBasis
                    ? usdKrw(feed, query, local, from, to)
                    : Optional.empty();
            PriceSeries prices = usdInstrument
                    ? FxBackfill.toKrw(local, fxRates).orElseThrow(() -> new SimulationException(ErrorCode.NO_PRICE_DATA,
                            i.name() + "은 달러 종목인데 " + from + " ~ " + to + " 원/달러 환율이 없어 원화로 환산할 수 없습니다."))
                    : local;
            Optional<UsdBasis> usd = Optional.empty();
            if (usdBasis) {
                PriceSeries rates = fxRates.orElseThrow(() -> new SimulationException(ErrorCode.NO_PRICE_DATA,
                        from + " ~ " + to + " 원/달러 환율이 없어 " + i.name() + "의 결과를 달러 기준으로 볼 수 없습니다."));
                usd = Optional.of(new UsdBasis(rates, usdInstrument ? Optional.of(local) : Optional.empty()));
            }
            SimulationCalculator.Pair pair = SimulationCalculator.calculate(query, prices, from, to, usd);
            results.add(new SimulationResponse.EtfResult(
                    new SimulationResponse.Etf(i.code(), i.name(), InstrumentUniverse.country(i.currency()), i.type().name(),
                            i.currency().name()),
                    pair.lumpSum(),
                    pair.recurring()));
        }
        return new SimulationResponse(from, to, query.currency().name(), results, SimulationResponse.DISCLAIMER);
    }

    /**
     * 기간의 실제 USD/KRW(fx_rate의 USDKRW). 원화 환산(미국 종목)과 달러 기준 보기에 같은 환율을 쓴다.
     * 매수일·종료일 근처에 환율이 없으면 요청자의 키로 그날만 받는다.
     *
     * @return 기간(과 그 앞 {@value #FX_LOOKBACK_DAYS}일)에 환율이 하나도 없으면 empty
     */
    private Optional<PriceSeries> usdKrw(
            MarketFeed feed, SimulationQuery query, PriceSeries local, LocalDate from, LocalDate to) {
        fx.ensure(feed, purchaseDates(query, local, from, to));
        return repository.loadFxRates(FxBackfill.PAIR, from.minusDays(FX_LOOKBACK_DAYS), to);
    }

    /** 적립 매수일 + 거치식 매수일(첫 거래일) + 종료일. */
    private static List<LocalDate> purchaseDates(SimulationQuery query, PriceSeries local, LocalDate from, LocalDate to) {
        NavigableSet<LocalDate> days = local.tradingDays(from, to);
        TreeSet<LocalDate> dates = new TreeSet<>();
        query.schedule().plan(BigDecimal.ONE).cashFlows(days).stream().map(CashFlow::date).forEach(dates::add);
        if (!days.isEmpty()) {
            dates.add(days.first());
        }
        dates.add(to);
        return List.copyOf(dates);
    }
}
