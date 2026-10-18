package com.uplog.ingest;

import com.uplog.calc.Trade;
import com.uplog.calc.portfolio.AccountFlow;
import com.uplog.calc.portfolio.Execution;
import com.uplog.core.CurrencyCode;
import com.uplog.core.WeekdayTradingCalendar;
import com.uplog.marketdata.MarketDataRepository;
import com.uplog.portfolio.PortfolioRepository;
import com.uplog.portfolio.PortfolioService;
import com.uplog.portfolio.broker.BrokerPort;
import com.uplog.portfolio.broker.BrokerPort.AccountRef;
import com.uplog.portfolio.broker.BrokerPort.FilledOrder;
import com.uplog.portfolio.connect.BrokerCredentials;
import com.uplog.portfolio.connect.BrokerException;
import com.uplog.portfolio.connect.CredentialReader;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 역산(BACKFILL)과 일일 수집(DAILY) 작업 본체(docs/design/account-connection.md "수집 규칙").
 *
 * <ul>
 *   <li>역산: 종료 주문 전부 → 체결(주문 id로 중복 제거) → 체결 시점 환율 → 종목 일봉(첫 체결 60거래일 전부터)
 *       → 일별 USD/KRW → 역산 구간 추정 입금 → account_day. 끝나면 직전 거래일 일일 수집을 큐에 넣는다.
 *   <li>일일: 커서 이후 새 체결 → 보유·현금 스냅샷 → 일봉·환율 → 추정 입출금(INFERRED) → 보유 대사 → account_day.
 * </ul>
 * 체결일은 그 시장의 현지 날짜다(미국 종목은 뉴욕 날짜).
 */
public class IngestRunner implements JobHandler {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final Logger log = LoggerFactory.getLogger(IngestRunner.class);

    private final BrokerPort broker;
    private final Supplier<CredentialReader> credentials;
    private final IngestStore store;
    private final IngestJobRepository jobs;
    private final MarketDataSync sync;
    private final MarketDataRepository marketData;
    private final PortfolioRepository portfolio;
    private final PortfolioService portfolioService;
    private final IngestProperties properties;
    private final Clock clock;

    public IngestRunner(
            BrokerPort broker,
            Supplier<CredentialReader> credentials,
            IngestStore store,
            IngestJobRepository jobs,
            MarketDataSync sync,
            MarketDataRepository marketData,
            PortfolioRepository portfolio,
            PortfolioService portfolioService,
            IngestProperties properties,
            Clock clock) {
        this.broker = broker;
        this.credentials = credentials;
        this.store = store;
        this.jobs = jobs;
        this.sync = sync;
        this.marketData = marketData;
        this.portfolio = portfolio;
        this.portfolioService = portfolioService;
        this.properties = properties;
        this.clock = clock;
    }

    private record Context(IngestStore.Target target, AccountRef ref) {

        BrokerCredentials credentials() {
            return ref.credentials();
        }

        long accountId() {
            return target.accountId();
        }
    }

    @Override
    public String run(IngestJob job, MarketDataSync.ProgressListener progress) {
        return switch (job.kind()) {
            case BACKFILL -> backfill(job, progress);
            case DAILY -> daily(job);
        };
    }

    // ---- 역산

    String backfill(IngestJob job, MarketDataSync.ProgressListener progress) {
        Context ctx = context(job.accountId());
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        LocalDate asOf = previousTradingDay(ctx.credentials(), today);

        List<FilledOrder> orders = fetchOrders(ctx.ref(), null, null, pages -> progress.report(Math.min(0.2, pages * 0.02)));
        Map<String, String> names = holdingNames(ctx.ref());
        registerInstruments(orders, names);
        List<IngestStore.NewTrade> trades = toTrades(ctx.credentials(), orders, f -> progress.report(0.2 + 0.2 * f));
        int inserted = store.insertTrades(ctx.accountId(), trades);
        progress.report(0.4);

        List<String> symbols = store.tradedSymbols(ctx.accountId());
        int candles = syncCandles(ctx, symbols, asOf, f -> progress.report(0.4 + 0.35 * f));
        int fxDays = store.firstUsdTradeDate(ctx.accountId())
                .map(first -> sync.syncFx(ctx.credentials(), first.minusDays(7), asOf, f -> progress.report(0.75 + 0.2 * f)))
                .orElse(0);

        int deposits = 0;
        if (properties.inferBackfillDeposits()) {
            store.replaceBackfillFlows(ctx.accountId(), List.of());
            List<AccountFlow> found = CashFlowInference.minimalDeposits(
                    portfolio.trades(ctx.accountId()), portfolio.cashFlows(ctx.accountId()));
            store.replaceBackfillFlows(ctx.accountId(), found);
            deposits = found.size();
        }
        orders.stream().map(FilledOrder::orderedAt).max(Comparator.naturalOrder())
                .ifPresent(at -> store.advanceCursor(ctx.accountId(), at.toInstant()));
        refresh(ctx.accountId());
        jobs.enqueue(ctx.accountId(), IngestJob.Kind.DAILY, asOf, clock.instant());
        String note = "역산: 체결 %d건(새로 %d건), 종목 %d개, 일봉 %d개, 환율 %d일, 추정 입금 %d건, 기준일 %s"
                .formatted(trades.size(), inserted, symbols.size(), candles, fxDays, deposits, asOf);
        log.info("계좌 {} {}", ctx.accountId(), note);
        return note;
    }

    // ---- 일일

    String daily(IngestJob job) {
        Context ctx = context(job.accountId());
        LocalDate asOf = resolveAsOf(ctx.credentials(), Objects.requireNonNull(job.asOf(), "asOf"));
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));

        LocalDate from = store.cursor(ctx.accountId())
                .map(at -> at.atZone(SEOUL).toLocalDate())
                .orElse(ctx.target().connectedAt())
                .minusDays(3);
        List<FilledOrder> orders = fetchOrders(ctx.ref(), from, today.isBefore(from) ? from : today, pages -> {});
        List<BrokerPort.Holding> holdings = broker.holdings(ctx.ref());
        Map<String, String> names = new HashMap<>();
        holdings.forEach(h -> names.put(h.symbol(), h.name()));
        registerInstruments(orders, names);
        holdings.forEach(h -> sync.ensureInstrument(h.symbol(), h.name(), h.market(), h.currency()));
        int inserted = store.insertTrades(ctx.accountId(), toTrades(ctx.credentials(), orders, f -> {}));

        List<String> symbols = store.tradedSymbols(ctx.accountId());
        syncCandles(ctx, symbols, asOf, f -> {});
        LocalDate fxFrom = store.firstUsdTradeDate(ctx.accountId()).map(d -> d.minusDays(7)).orElse(asOf.minusDays(7));
        sync.syncFx(ctx.credentials(), fxFrom, asOf, MarketDataSync.ProgressListener.NONE);
        BigDecimal fx = fxAt(ctx.credentials(), asOf.atTime(MarketDataSync.FX_TIME).atZone(SEOUL).toOffsetDateTime(), asOf);

        store.replaceHoldings(ctx.accountId(), asOf, holdings);
        IngestStore.CashSnapshot cash = new IngestStore.CashSnapshot(asOf,
                broker.cash(ctx.ref(), CurrencyCode.KRW), broker.cash(ctx.ref(), CurrencyCode.USD), fx);
        store.upsertCash(ctx.accountId(), cash);

        BigDecimal flow = store.cashBefore(ctx.accountId(), asOf)
                .map(prev -> CashFlowInference.inferred(prev, cash, store.tradeCash(ctx.accountId(), prev.date(), asOf)))
                .orElseGet(() -> CashFlowInference.reconcileWithLedger(cash,
                        portfolio.trades(ctx.accountId()).stream().filter(t -> !t.date().isAfter(asOf)).toList(),
                        portfolio.cashFlows(ctx.accountId()).stream().filter(f -> f.date().isBefore(asOf)).toList()));
        store.replaceInferredFlow(ctx.accountId(), asOf, flow);

        List<String> mismatches = reconcile(portfolio.trades(ctx.accountId()), holdings);
        if (!mismatches.isEmpty()) {
            log.warn("계좌 {} 보유 대사 불일치 {}건: {}", ctx.accountId(), mismatches.size(), mismatches);
        }
        store.deleteGap(ctx.accountId(), asOf);
        orders.stream().map(FilledOrder::orderedAt).max(Comparator.naturalOrder())
                .ifPresent(at -> store.advanceCursor(ctx.accountId(), at.toInstant()));
        refresh(ctx.accountId());
        String note = "일일 %s: 새 체결 %d건, 보유 %d종목, 현금 %s원 + %s달러, 추정 유입 %s원%s".formatted(
                asOf, inserted, holdings.size(), cash.krw().toPlainString(), cash.usd().toPlainString(), flow.toPlainString(),
                mismatches.isEmpty() ? "" : ", 대사 불일치 " + String.join("; ", mismatches));
        log.info("계좌 {} {}", ctx.accountId(), note);
        return note;
    }

    /**
     * 리플레이한 보유 수량과 증권사 보유 수량을 맞춰 본다.
     *
     * @return "종목: 기록 x / 증권사 y" 목록
     */
    static List<String> reconcile(List<Execution> executions, List<BrokerPort.Holding> holdings) {
        Map<String, BigDecimal> replayed = new TreeMap<>();
        for (Execution e : executions) {
            BigDecimal q = e.side() == Trade.Side.BUY ? e.quantity() : e.quantity().negate();
            replayed.merge(e.instrument(), q, BigDecimal::add);
        }
        Map<String, BigDecimal> actual = new TreeMap<>();
        holdings.forEach(h -> actual.merge(h.symbol(), h.quantity(), BigDecimal::add));
        List<String> mismatches = new ArrayList<>();
        java.util.Set<String> codes = new java.util.TreeSet<>(replayed.keySet());
        codes.addAll(actual.keySet());
        for (String code : codes) {
            BigDecimal mine = replayed.getOrDefault(code, BigDecimal.ZERO);
            BigDecimal theirs = actual.getOrDefault(code, BigDecimal.ZERO);
            if (mine.compareTo(theirs) != 0) {
                mismatches.add(code + ": 기록 " + mine.stripTrailingZeros().toPlainString()
                        + " / 증권사 " + theirs.stripTrailingZeros().toPlainString());
            }
        }
        return mismatches;
    }

    // ---- 공통

    private Context context(long accountId) {
        IngestStore.Target target = store.target(accountId)
                .orElseThrow(() -> new IllegalStateException("계좌가 없습니다: " + accountId));
        if (!"ACTIVE".equals(target.status())) {
            throw new IngestErrors.AccountDisconnectedException(accountId);
        }
        if (target.accountSeq() == null || target.accountSeq().isBlank()) {
            throw new IllegalStateException("증권사 계좌가 정해지지 않은 계좌입니다: " + accountId);
        }
        BrokerCredentials c = credentials.get().read(accountId);
        return new Context(target, new AccountRef(c, target.accountSeq()));
    }

    /** 체결·입출금이 하나라도 있을 때만 account_day를 다시 쓴다(없으면 리플레이할 구간이 없다). */
    private void refresh(long accountId) {
        if (!portfolio.trades(accountId).isEmpty() || !portfolio.cashFlows(accountId).isEmpty()) {
            portfolioService.refresh(accountId);
        }
    }

    /** 장 운영 캘린더로 직전 국내 영업일을 구하고, 캘린더를 못 받으면 평일 기준으로 셈한다. */
    LocalDate previousTradingDay(BrokerCredentials c, LocalDate today) {
        try {
            return broker.marketDays(c, BrokerPort.Market.KR, today).previous();
        } catch (BrokerException e) {
            if (e.reason() != BrokerException.Reason.UNAVAILABLE && e.reason() != BrokerException.Reason.RATE_LIMITED) {
                throw e;
            }
            return WeekdayTradingCalendar.weekdaysOnly().previous(today);
        }
    }

    /** 기준일이 휴장일이면 그 전 영업일로 바꾼다. */
    private LocalDate resolveAsOf(BrokerCredentials c, LocalDate asOf) {
        try {
            BrokerPort.MarketDays days = broker.marketDays(c, BrokerPort.Market.KR, asOf);
            return days.todayOpen() ? asOf : days.previous();
        } catch (BrokerException e) {
            if (e.reason() != BrokerException.Reason.UNAVAILABLE && e.reason() != BrokerException.Reason.RATE_LIMITED) {
                throw e;
            }
            return asOf;
        }
    }

    /** @param pageListener 받은 페이지 수 */
    private List<FilledOrder> fetchOrders(AccountRef ref, LocalDate from, LocalDate to, java.util.function.IntConsumer pageListener) {
        List<FilledOrder> orders = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            BrokerPort.OrderPage page = broker.closedOrders(ref, from, to, cursor);
            orders.addAll(page.orders());
            cursor = page.nextCursor();
            pageListener.accept(++pages);
        } while (cursor != null);
        orders.sort(Comparator.comparing(FilledOrder::filledAt).thenComparing(FilledOrder::orderId));
        return orders;
    }

    private Map<String, String> holdingNames(AccountRef ref) {
        Map<String, String> names = new HashMap<>();
        broker.holdings(ref).forEach(h -> names.put(h.symbol(), h.name()));
        return names;
    }

    private void registerInstruments(List<FilledOrder> orders, Map<String, String> names) {
        Map<String, FilledOrder> first = new LinkedHashMap<>();
        orders.forEach(o -> first.putIfAbsent(o.symbol(), o));
        first.forEach((symbol, o) -> sync.ensureInstrument(symbol, names.get(symbol), o.market(), o.currency()));
    }

    private List<IngestStore.NewTrade> toTrades(BrokerCredentials c, List<FilledOrder> orders, MarketDataSync.ProgressListener progress) {
        List<IngestStore.NewTrade> trades = new ArrayList<>();
        Map<Instant, BigDecimal> fxMemo = new HashMap<>();
        long usdCount = orders.stream().filter(o -> o.currency() == CurrencyCode.USD).count();
        int usdDone = 0;
        for (FilledOrder o : orders) {
            ZoneId zone = o.market() == BrokerPort.Market.US ? NEW_YORK : SEOUL;
            LocalDate date = o.filledAt().atZoneSameInstant(zone).toLocalDate();
            BigDecimal fx = BigDecimal.ONE;
            if (o.currency() == CurrencyCode.USD) {
                Instant minute = o.filledAt().toInstant().truncatedTo(ChronoUnit.MINUTES);
                fx = fxMemo.computeIfAbsent(minute, m -> fxAt(c, o.filledAt(), date));
                progress.report(++usdDone / (double) usdCount);
            }
            trades.add(new IngestStore.NewTrade(o.orderId(), date, o.symbol(), o.side(), o.filledQuantity(),
                    o.averageFilledPrice(), fx, o.commission(), o.tax()));
        }
        progress.report(1.0);
        return trades;
    }

    /** 그 시각 환율. 증권사에 없으면 저장된 그날(없으면 직전) 환율. */
    private BigDecimal fxAt(BrokerCredentials c, OffsetDateTime at, LocalDate date) {
        Optional<BigDecimal> rate = broker.usdKrw(c, at);
        if (rate.isPresent()) {
            return rate.get();
        }
        return marketData.loadFxRates(MarketDataSync.USD_KRW, date.minusDays(30), date)
                .flatMap(s -> s.closeOnOrBefore(date))
                .orElseThrow(() -> new IllegalStateException("USD/KRW 환율을 구할 수 없습니다: " + at));
    }

    private int syncCandles(Context ctx, List<String> symbols, LocalDate asOf, MarketDataSync.ProgressListener progress) {
        Map<String, NavigableMap<LocalDate, BigDecimal>> fallback = new HashMap<>();
        for (Execution e : portfolio.trades(ctx.accountId())) {
            fallback.computeIfAbsent(e.instrument(), k -> new TreeMap<>()).put(e.date(), e.priceLocal());
        }
        int total = 0;
        for (int i = 0; i < symbols.size(); i++) {
            String symbol = symbols.get(i);
            LocalDate first = store.firstTradeDate(ctx.accountId(), symbol).orElse(asOf);
            LocalDate from = sync.lookbackStart(first, properties.candleLookback());
            total += sync.syncCandles(ctx.credentials(), symbol, from, asOf, fallback.get(symbol));
            progress.report((i + 1.0) / symbols.size());
        }
        return total;
    }
}
