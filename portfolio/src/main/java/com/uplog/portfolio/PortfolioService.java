package com.uplog.portfolio;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.uplog.calc.BenchmarkReplay;
import com.uplog.calc.CashFlow;
import com.uplog.calc.DailyReturn;
import com.uplog.calc.PriceSeries;
import com.uplog.calc.ReplayPoint;
import com.uplog.calc.Streaks;
import com.uplog.calc.Twr;
import com.uplog.calc.portfolio.AccountFlow;
import com.uplog.calc.portfolio.AccountLog;
import com.uplog.calc.portfolio.Dividend;
import com.uplog.calc.portfolio.Execution;
import com.uplog.calc.portfolio.FxConversion;
import com.uplog.calc.portfolio.FxDecomposition;
import com.uplog.calc.portfolio.HoldingPeriods;
import com.uplog.calc.portfolio.InstrumentDay;
import com.uplog.calc.portfolio.PnlWaterfall;
import com.uplog.calc.portfolio.PortfolioDay;
import com.uplog.calc.portfolio.RealizedSale;
import com.uplog.calc.portfolio.SaleShadow;
import com.uplog.calc.portfolio.Underwater;
import com.uplog.core.CurrencyCode;
import com.uplog.marketdata.Instrument;
import com.uplog.marketdata.ListedInstrument;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.marketdata.PriceDataVersion;
import com.uplog.portfolio.api.PortfolioDtos.AccountDto;
import com.uplog.portfolio.api.PortfolioDtos.BenchmarkDto;
import com.uplog.portfolio.api.PortfolioDtos.BucketDto;
import com.uplog.portfolio.api.PortfolioDtos.CashFlowDto;
import com.uplog.portfolio.api.PortfolioDtos.ChangeAfter;
import com.uplog.portfolio.api.PortfolioDtos.ContributionDto;
import com.uplog.portfolio.api.PortfolioDtos.CurrentDrawdown;
import com.uplog.portfolio.api.PortfolioDtos.DayPnl;
import com.uplog.portfolio.api.PortfolioDtos.DayResponse;
import com.uplog.portfolio.api.PortfolioDtos.EpisodeDto;
import com.uplog.portfolio.api.PortfolioDtos.FxItemDto;
import com.uplog.portfolio.api.PortfolioDtos.FxResponse;
import com.uplog.portfolio.api.PortfolioDtos.FxTotal;
import com.uplog.portfolio.api.PortfolioDtos.HoldingDetailResponse;
import com.uplog.portfolio.api.PortfolioDtos.HoldingDto;
import com.uplog.portfolio.api.PortfolioDtos.HoldingPeriodDto;
import com.uplog.portfolio.api.PortfolioDtos.HoldingPeriodsResponse;
import com.uplog.portfolio.api.PortfolioDtos.InstrumentDto;
import com.uplog.portfolio.api.PortfolioDtos.LogDay;
import com.uplog.portfolio.api.PortfolioDtos.LogResponse;
import com.uplog.portfolio.api.PortfolioDtos.LogStats;
import com.uplog.portfolio.api.PortfolioDtos.LogStreaks;
import com.uplog.portfolio.api.PortfolioDtos.PositionDto;
import com.uplog.portfolio.api.PortfolioDtos.PricePoint;
import com.uplog.portfolio.api.PortfolioDtos.SaleDto;
import com.uplog.portfolio.api.PortfolioDtos.SalesResponse;
import com.uplog.portfolio.api.PortfolioDtos.SalesSummary;
import com.uplog.portfolio.api.PortfolioDtos.ShadowPoint;
import com.uplog.portfolio.api.PortfolioDtos.ShadowResponse;
import com.uplog.portfolio.api.PortfolioDtos.ShadowSummary;
import com.uplog.portfolio.api.PortfolioDtos.Streak;
import com.uplog.portfolio.api.PortfolioDtos.StreakStats;
import com.uplog.portfolio.api.PortfolioDtos.SummaryResponse;
import com.uplog.portfolio.api.PortfolioDtos.TradeDto;
import com.uplog.portfolio.api.PortfolioDtos.UnderwaterPoint;
import com.uplog.portfolio.api.PortfolioDtos.UnderwaterResponse;
import com.uplog.portfolio.api.PortfolioDtos.WaterfallItem;
import com.uplog.portfolio.api.PortfolioDtos.WaterfallResponse;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

/**
 * 기능 B 조회. 현재 계좌의 원시 데이터를 시세에 리플레이한 {@link PortfolioSnapshot}을 (계좌, 계좌 데이터 버전, 시세 버전)으로
 * 캐시하고, 엔드포인트마다 그 위에서 계산·반올림한다. 금액은 원 단위 반올림(HALF_UP), …Local은 소수 4자리.
 */
@Service
public class PortfolioService {

    /** account_day 계산 규칙 버전. 리플레이 규칙을 바꾸면 올린다. */
    public static final int CALC_VERSION = 1;

    static final String USD_KRW = "USDKRW";
    static final Map<String, String> BENCHMARK_NAMES = Map.of("SPX", "S&P500", "KOSPI200", "코스피200");

    private static final int CHART_LOOKBACK = 60;
    private static final int DETAIL_HORIZON = 20;

    private record CacheKey(long accountId, long accountVersion, long priceVersion) {}

    private final PortfolioRepository repository;
    private final MarketDataRepository marketData;
    private final PortfolioDataVersion accountVersion;
    private final PriceDataVersion priceVersion;
    private final Cache<CacheKey, PortfolioSnapshot> cache = Caffeine.newBuilder()
            .maximumSize(16)
            .expireAfterWrite(Duration.ofHours(12))
            .build();

    public PortfolioService(
            PortfolioRepository repository,
            MarketDataRepository marketData,
            PortfolioDataVersion accountVersion,
            PriceDataVersion priceVersion) {
        this.repository = repository;
        this.marketData = marketData;
        this.accountVersion = accountVersion;
        this.priceVersion = priceVersion;
    }

    // ---- 계산 묶음

    PortfolioSnapshot snapshot() {
        PortfolioRepository.Account account = repository.currentAccount()
                .orElseThrow(() -> new PortfolioException(
                        PortfolioErrorCode.ACCOUNT_NOT_CONNECTED, "연결된 계좌가 없습니다. 증권 계좌를 먼저 연결해 주세요."));
        CacheKey key = new CacheKey(account.id(), accountVersion.current(), priceVersion.current());
        return cache.get(key, k -> load(account));
    }

    private PortfolioSnapshot load(PortfolioRepository.Account account) {
        List<Execution> trades = repository.trades(account.id());
        List<AccountFlow> flows = repository.cashFlows(account.id());
        List<Dividend> dividends = repository.dividends(account.id());
        LocalDate first = Stream.concat(trades.stream().map(Execution::date), flows.stream().map(AccountFlow::date))
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new PortfolioException(
                        PortfolioErrorCode.ACCOUNT_NOT_CONNECTED, "계좌에 아직 입금·체결 기록이 없습니다."));
        // 종목 차트는 첫 거래 60거래일 전부터 그리므로 넉넉히 1년 앞부터 읽는다.
        LocalDate from = first.minusYears(1);
        LocalDate to = first.plusYears(100);
        Set<String> codes = new TreeSet<>();
        trades.forEach(t -> codes.add(t.instrument()));
        dividends.forEach(d -> codes.add(d.instrument()));
        Map<String, Instrument> instruments = new TreeMap<>();
        Map<String, PriceSeries> closes = new TreeMap<>();
        for (String code : codes) {
            Instrument instrument = marketData.findByCode(code).map(ListedInstrument::instrument)
                    .orElseThrow(() -> new IllegalStateException("종목 정보가 없습니다: " + code));
            instruments.put(code, instrument);
            closes.put(code, marketData.loadCloses(instrument.id(), from, to)
                    .orElseThrow(() -> new IllegalStateException("종목 시세가 없습니다: " + code)));
        }
        PriceSeries usdKrw = marketData.loadFxRates(USD_KRW, from, to).orElse(null);
        return PortfolioSnapshot.build(account, instruments, closes, usdKrw, trades, flows, dividends,
                repository.gaps(account.id()));
    }

    /** 원시 데이터가 바뀐 뒤(수집·시드) 부른다. 캐시를 무효화하고 account_day를 다시 쓴다. */
    public void refresh() {
        accountVersion.bump();
        if (repository.currentAccount().isEmpty()) {
            return;
        }
        PortfolioSnapshot s = snapshot();
        List<PortfolioRepository.AccountDayRow> rows = s.log().stream()
                .map(e -> new PortfolioRepository.AccountDayRow(e.date(), s.sources().get(e.date()),
                        e.value().setScale(2, RoundingMode.HALF_UP), e.netCashFlow().setScale(2, RoundingMode.HALF_UP),
                        e.pnl().setScale(2, RoundingMode.HALF_UP), e.dailyReturn(), e.twrIndex()))
                .toList();
        repository.replaceAccountDays(s.account().id(), rows, CALC_VERSION);
    }

    // ---- 메인

    public SummaryResponse summary() {
        PortfolioSnapshot s = snapshot();
        AccountLog.Entry last = s.lastObserved();
        long total = won(last.value());
        long deposits = won(last.netDeposits());
        return new SummaryResponse(
                new AccountDto(s.account().name(), s.account().broker(), s.account().sample(), s.firstDate(), s.lastDate()),
                last.date(), total, deposits, total - deposits, rate(last.twrIndex() - 1.0), won(last.pnl()),
                rate(last.dailyReturn()));
    }

    public LogResponse log(String rawYear) {
        PortfolioSnapshot s = snapshot();
        List<Integer> years = s.log().stream().map(e -> e.date().getYear()).distinct().toList();
        Integer parsed = PortfolioParams.year(rawYear, years);
        int year = parsed == null ? s.lastDate().getYear() : parsed;
        List<AccountLog.Entry> entries = s.log().stream().filter(e -> e.date().getYear() == year).toList();

        List<LogDay> days = new ArrayList<>();
        List<DailyReturn> signs = new ArrayList<>();
        int gain = 0;
        int loss = 0;
        int flat = 0;
        long totalPnl = 0;
        DayPnl best = null;
        DayPnl worst = null;
        for (AccountLog.Entry e : entries) {
            DaySource source = s.sources().get(e.date());
            long pnl = won(e.pnl());
            days.add(new LogDay(e.date(), won(e.value()), won(e.netCashFlow()), pnl, rate(e.dailyReturn()), source.name()));
            if (source == DaySource.MISSING) {
                continue;
            }
            signs.add(new DailyReturn(e.date(), Long.signum(pnl), 0.0));
            if (pnl > 0) {
                gain++;
            } else if (pnl < 0) {
                loss++;
            } else {
                flat++;
            }
            totalPnl += pnl;
            if (best == null || pnl > best.pnl()) {
                best = new DayPnl(e.date(), pnl);
            }
            if (worst == null || pnl < worst.pnl()) {
                worst = new DayPnl(e.date(), pnl);
            }
        }
        return new LogResponse(year, years, entries.getFirst().date(), entries.getLast().date(), days,
                new LogStats(gain, loss, flat, totalPnl, best, worst),
                new LogStreaks(streaks(Streaks.gains(signs)), streaks(Streaks.losses(signs))));
    }

    public List<HoldingDto> holdings() {
        PortfolioSnapshot s = snapshot();
        PortfolioDay last = s.history().last();
        return last.instruments().values().stream()
                .filter(d -> d.position().isOpen())
                .map(d -> {
                    PositionDto p = position(d);
                    double weight = last.value().signum() == 0 ? 0.0
                            : d.value().divide(last.value(), MathContext.DECIMAL64).doubleValue();
                    return new HoldingDto(instrument(s, d.instrument()), p.quantity(), p.avgCostLocal(), p.priceLocal(),
                            p.fxRate(), p.value(), p.profit(), p.returnRate(), rate(weight));
                })
                .sorted(Comparator.comparingLong(HoldingDto::value).reversed())
                .toList();
    }

    public DayResponse day(LocalDate date) {
        PortfolioSnapshot s = snapshot();
        List<AccountLog.Entry> observed = s.observed();
        int i = 0;
        while (i < observed.size() && !observed.get(i).date().equals(date)) {
            i++;
        }
        if (i == observed.size()) {
            throw new PortfolioException(PortfolioErrorCode.DATE_NOT_FOUND, date + "은(는) 기록이 있는 거래일이 아닙니다.");
        }
        AccountLog.Entry e = observed.get(i);
        List<ContributionDto> contributions = e.contributions().stream()
                .map(c -> new ContributionDto(instrument(s, c.instrument()), won(c.pnl()), rate(c.dailyReturn()), rate(c.weight())))
                .toList();
        List<CashFlowDto> cashFlows = new ArrayList<>();
        e.flows().forEach(f -> cashFlows.add(new CashFlowDto(f.type().name(), won(f.amount()), null)));
        e.dividendEvents().forEach(d -> cashFlows.add(new CashFlowDto("DIVIDEND", won(d.amount()), instrument(s, d.instrument()))));
        return new DayResponse(
                e.date(),
                i > 0 ? observed.get(i - 1).date() : null,
                i < observed.size() - 1 ? observed.get(i + 1).date() : null,
                won(e.value()), won(e.netCashFlow()), won(e.pnl()), rate(e.dailyReturn()),
                contributions, e.executions().stream().map(t -> trade(s, t)).toList(), cashFlows);
    }

    public HoldingDetailResponse holding(String code) {
        PortfolioSnapshot s = snapshot();
        List<Execution> trades = s.trades().stream().filter(t -> t.instrument().equals(code)).toList();
        if (trades.isEmpty()) {
            throw new PortfolioException(PortfolioErrorCode.INSTRUMENT_NOT_FOUND, "이 종목의 보유 이력이 없습니다: " + code);
        }
        InstrumentDay now = s.history().last().instruments().get(code);
        boolean open = now != null && now.position().isOpen();
        BigDecimal realized = s.history().sales().stream()
                .filter(x -> x.execution().instrument().equals(code))
                .map(RealizedSale::realizedProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dividends = s.dividends().stream()
                .filter(d -> d.instrument().equals(code))
                .map(Dividend::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PriceSeries prices = s.closes().get(code);
        LocalDate chartFrom = prices.nthTradingDayBefore(trades.getFirst().date(), CHART_LOOKBACK);
        return new HoldingDetailResponse(
                instrument(s, code),
                open ? position(now) : null,
                won(realized),
                won(dividends),
                points(prices.range(chartFrom, s.lastDate())),
                trades.stream().map(t -> trade(s, t)).toList(),
                periods(s).stream().filter(p -> p.instrument().code().equals(code)).toList(),
                sales(s, DETAIL_HORIZON).stream().filter(x -> x.instrument().code().equals(code)).toList(),
                open && s.currencies().get(code) == CurrencyCode.USD ? fxItem(s, FxDecomposition.item(now)) : null);
    }

    // ---- 인사이트

    public SalesResponse sales(int horizon) {
        PortfolioSnapshot s = snapshot();
        List<SaleShadow.Judged> judged = judge(s, horizon);
        SaleShadow.Summary summary = SaleShadow.summarize(judged);
        return new SalesResponse(horizon,
                new SalesSummary(summary.sales(), summary.judged(), summary.good(), rate(summary.winRate()),
                        summary.avoidedLoss(), summary.missedGain()),
                sales(s, horizon));
    }

    public ShadowResponse shadow(String benchmark) {
        PortfolioSnapshot s = snapshot();
        Instrument index = marketData.findByCode(benchmark).map(ListedInstrument::instrument)
                .orElseThrow(() -> new PortfolioException(
                        PortfolioErrorCode.INSTRUMENT_NOT_FOUND, "벤치마크 시세가 없습니다: " + benchmark));
        PriceSeries local = marketData.loadCloses(index.id(), s.firstDate().minusDays(10), s.lastDate())
                .orElseThrow(() -> new PortfolioException(
                        PortfolioErrorCode.INSTRUMENT_NOT_FOUND, "벤치마크 시세가 없습니다: " + benchmark));
        PriceSeries krw = local;
        if (index.currency() == CurrencyCode.USD) {
            PriceSeries fx = s.usdKrw() != null ? s.usdKrw()
                    : marketData.loadFxRates(USD_KRW, s.firstDate().minusYears(1), s.lastDate())
                            .orElseThrow(() -> new PortfolioException(
                                    PortfolioErrorCode.INSTRUMENT_NOT_FOUND, "USD/KRW 환율이 없습니다."));
            krw = FxConversion.toKrw(local, fx);
        }
        List<CashFlow> flows = s.flows().stream().map(f -> new CashFlow(f.date(), f.signedAmount())).toList();
        List<ReplayPoint> points = BenchmarkReplay.replay(flows, krw, s.lastDate());
        NavigableMap<LocalDate, BigDecimal> shadowValue = new TreeMap<>();
        points.forEach(p -> shadowValue.put(p.date(), p.value()));
        double shadowTwr = points.isEmpty() ? 0.0
                : Twr.compute(points.stream().map(ReplayPoint::toValuation).toList()).getLast().twrIndex() - 1.0;

        List<ShadowPoint> series = s.observed().stream()
                .map(e -> {
                    Entry<LocalDate, BigDecimal> shadow = shadowValue.floorEntry(e.date());
                    return new ShadowPoint(e.date(), won(e.value()), shadow == null ? 0 : won(shadow.getValue()),
                            won(e.netDeposits()));
                })
                .toList();
        ShadowPoint last = series.getLast();
        List<BenchmarkDto> benchmarks = PortfolioParams.BENCHMARKS.stream()
                .map(code -> new BenchmarkDto(code, BENCHMARK_NAMES.get(code)))
                .toList();
        return new ShadowResponse(benchmarks, benchmark, series, new ShadowSummary(
                last.actual(), last.shadow(), last.actual() - last.shadow(),
                rate(s.lastObserved().twrIndex() - 1.0), rate(shadowTwr)));
    }

    public WaterfallResponse waterfall(String period) {
        PortfolioSnapshot s = snapshot();
        LocalDate to = s.lastObserved().date();
        LocalDate anchor = switch (period) {
            case "1M" -> to.minusMonths(1);
            case "3M" -> to.minusMonths(3);
            case "YTD" -> LocalDate.of(to.getYear() - 1, 12, 31);
            case "1Y" -> to.minusYears(1);
            default -> null;
        };
        AccountLog.Entry start = s.observed().getFirst();
        if (anchor != null) {
            for (AccountLog.Entry e : s.observed()) {
                if (!e.date().isAfter(anchor)) {
                    start = e;
                }
            }
        }
        PnlWaterfall.Result r = PnlWaterfall.of(s.history(),
                s.history().indexOf(start.date()).orElseThrow(), s.history().indexOf(to).orElseThrow());
        List<WaterfallItem> items = r.items().stream()
                .map(i -> new WaterfallItem(i.kind().name(), label(s, i),
                        i.instrument() == null ? null : instrument(s, i.instrument()), i.amount()))
                .toList();
        return new WaterfallResponse(period, r.from(), r.to(), r.startValue(), r.netCashFlow(), r.endValue(), items, r.total());
    }

    public HoldingPeriodsResponse holdingPeriods() {
        PortfolioSnapshot s = snapshot();
        List<HoldingPeriodDto> periods = periods(s);
        List<BucketDto> buckets = new ArrayList<>();
        for (HoldingPeriods.Bucket bucket : HoldingPeriods.Bucket.values()) {
            List<HoldingPeriodDto> in = periods.stream().filter(p -> HoldingPeriods.Bucket.of(p.days()) == bucket).toList();
            int n = in.size();
            buckets.add(new BucketDto(bucket.name(), bucket.label(), n,
                    n == 0 ? 0.0 : rate((double) in.stream().filter(p -> p.profit() > 0).count() / n),
                    n == 0 ? 0.0 : rate(in.stream().mapToDouble(HoldingPeriodDto::returnRate).average().orElse(0.0)),
                    in.stream().mapToLong(HoldingPeriodDto::profit).sum()));
        }
        return new HoldingPeriodsResponse(periods, buckets);
    }

    public FxResponse fx() {
        PortfolioSnapshot s = snapshot();
        FxDecomposition.Result r = FxDecomposition.of(s.history().last(), s.currencies());
        return new FxResponse(s.lastDate(), r.items().stream().map(i -> fxItem(s, i)).toList(),
                new FxTotal(r.total().priceEffect(), r.total().fxEffect(), r.total().profit()));
    }

    public UnderwaterResponse underwater() {
        Underwater.Result r = Underwater.of(snapshot().observed());
        return new UnderwaterResponse(
                r.series().stream().map(p -> new UnderwaterPoint(p.date(), rate(p.drawdown()), won(p.drawdownAmount()))).toList(),
                r.episodes().stream()
                        .map(e -> new EpisodeDto(e.peak(), e.trough(), e.recovered().orElse(null), rate(e.depth()),
                                e.daysToTrough(), e.daysToRecover().orElse(null)))
                        .toList(),
                new CurrentDrawdown(rate(r.current().drawdown()), r.current().peak(), r.current().days(),
                        rate(r.current().deepestPast()), r.current().deeperThanPast()));
    }

    // ---- 변환

    private List<SaleShadow.Judged> judge(PortfolioSnapshot s, int horizon) {
        return s.history().sales().stream()
                .map(sale -> SaleShadow.judge(sale, s.closes().get(sale.execution().instrument()),
                        s.fxFor(sale.execution().instrument()), s.lastDate(), horizon))
                .toList();
    }

    /** 최근 매도 먼저. id는 날짜 순서로 매긴 s-1, s-2 … */
    private List<SaleDto> sales(PortfolioSnapshot s, int horizon) {
        List<SaleDto> sales = new ArrayList<>();
        for (SaleShadow.Judged j : judge(s, horizon)) {
            RealizedSale sale = j.sale();
            Execution e = sale.execution();
            PriceSeries prices = s.closes().get(e.instrument());
            sales.add(new SaleDto(
                    "s-" + sale.sequence(), sale.day(), instrument(s, e.instrument()), e.quantity().doubleValue(),
                    local(e.priceLocal()), e.fxRate().doubleValue(), won(e.grossKrw()), won(sale.realizedProfit()),
                    local(j.priceNowLocal()), rate(j.changeSinceSale()),
                    new ChangeAfter(change(j, 5), change(j, 20), change(j, 60)),
                    j.verdict().name(), j.afterEffect(),
                    points(prices.range(prices.nthTradingDayBefore(sale.day(), CHART_LOOKBACK), s.lastDate()))));
        }
        return sales.reversed();
    }

    private static Double change(SaleShadow.Judged j, int n) {
        return j.changeAfter().get(n).map(PortfolioService::rate).orElse(null);
    }

    /** open 오름차순, id는 p-1, p-2 … */
    private List<HoldingPeriodDto> periods(PortfolioSnapshot s) {
        List<HoldingPeriodDto> periods = new ArrayList<>();
        for (HoldingPeriods.Period p : s.periods()) {
            periods.add(new HoldingPeriodDto("p-" + (periods.size() + 1), instrument(s, p.instrument()), p.open(),
                    p.close().orElse(null), p.days(), rate(p.returnRate()), won(p.profit()), p.isOpen() ? "OPEN" : "CLOSED"));
        }
        return periods;
    }

    private static PositionDto position(InstrumentDay d) {
        long value = won(d.value());
        long profit = value - won(d.position().costKrw());
        double returnRate = d.position().costKrw().signum() == 0 ? 0.0
                : BigDecimal.valueOf(profit).divide(d.position().costKrw(), MathContext.DECIMAL64).doubleValue();
        return new PositionDto(d.position().quantity().doubleValue(), local(d.position().avgCostLocal()), local(d.priceLocal()),
                d.fxRate().doubleValue(), value, profit, rate(returnRate));
    }

    private static FxItemDto fxItem(PortfolioSnapshot s, FxDecomposition.Item i) {
        return new FxItemDto(instrument(s, i.instrument()), i.quantity().doubleValue(), local(i.avgCostLocal()),
                local(i.avgFxRate()), local(i.priceLocal()), i.fxRate().doubleValue(), i.priceEffect(), i.fxEffect(), i.profit());
    }

    private static String label(PortfolioSnapshot s, PnlWaterfall.Item item) {
        return switch (item.kind()) {
            case INSTRUMENT -> s.instruments().get(item.instrument()).name();
            case DIVIDEND -> "배당";
            case FEE -> "수수료";
            case TAX -> "세금";
            case FX -> "환율";
            case OTHER -> "기타";
        };
    }

    private static TradeDto trade(PortfolioSnapshot s, Execution e) {
        return new TradeDto(e.date(), instrument(s, e.instrument()), e.side().name(), e.quantity().doubleValue(),
                local(e.priceLocal()), e.fxRate().doubleValue(), won(e.grossKrw()), won(e.feeKrw()), won(e.taxKrw()));
    }

    /** market은 통화로 정한다(원화 KR, 달러 US): 시세 테이블의 market(KRX 등)은 계약의 값과 다를 수 있다. */
    static InstrumentDto instrument(PortfolioSnapshot s, String code) {
        Instrument i = s.instruments().get(code);
        return new InstrumentDto(code, i.name(), i.currency() == CurrencyCode.KRW ? "KR" : "US", i.currency().name());
    }

    private static List<PricePoint> points(Map<LocalDate, BigDecimal> closes) {
        List<PricePoint> points = new ArrayList<>(closes.size());
        closes.forEach((date, close) -> points.add(new PricePoint(date, local(close))));
        return points;
    }

    private static StreakStats streaks(Streaks.Stats stats) {
        return new StreakStats(stats.longest().map(PortfolioService::streak).orElse(null),
                stats.current().map(PortfolioService::streak).orElse(null));
    }

    private static Streak streak(Streaks.Streak s) {
        return new Streak(s.length(), s.start(), s.end());
    }

    static long won(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    static double local(BigDecimal amount) {
        return amount.setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    /** 비율은 소수 6자리로 자른다(JSON 크기). */
    static double rate(double value) {
        return Math.round(value * 1e6) / 1e6;
    }
}
