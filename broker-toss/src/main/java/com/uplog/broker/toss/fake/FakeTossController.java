package com.uplog.broker.toss.fake;

import com.uplog.broker.toss.RateLimitGroup;
import com.uplog.broker.toss.fake.FakeTossBook.Order;
import com.uplog.broker.toss.fake.FakeTossBook.Position;
import com.uplog.broker.toss.fake.FakeTossMarket.Symbol;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가짜 토스 서버(로컬 개발·테스트용). 실제 명세(1.2.24)와 같은 경로·필드 이름·형식(숫자는 문자열, 성공은 {@code result} envelope,
 * 토큰은 OAuth2 형식)으로 결정적 데이터를 준다. {@code uplog.toss.fake-server.enabled=true}일 때만 뜬다(fake-toss 프로필).
 *
 * <ul>
 *   <li>{@code client_secret=invalid} → 401 invalid_client("...: client_secret"), {@code client_id}가 {@code bad-id}로 시작 → ("...: client_id")
 *   <li>{@code client_id}가 {@code ip-blocked}로 시작 → 403 access_denied("IP address not allowed")
 *   <li>{@code client_id}가 {@code multi}로 시작 → 종합매매 계좌 2개, {@code no-account}로 시작 → 계좌 없음
 *   <li>클라이언트당 토큰 1개: 새로 받으면 이전 토큰은 401 token-revoked
 *   <li>그룹별 한도(문서 수치 × {@code uplog.toss.fake-server.rate-limit-multiplier})와 X-RateLimit-* 헤더, 넘으면 429 + Retry-After
 *   <li>{@code client_id}가 {@code market-blocked}로 시작 → 계좌 조회는 되고 시세·종목·환율 조회만 403 ip-not-allowed
 *   <li>종목 목록·상세({@code /api/v1/stocks/all}, {@code /api/v1/stocks}): 계좌 종목 8개 + 목록 전용 종목 몇 개(국내·미국 ETF·주식, ETN 1개)
 * </ul>
 */
@RestController
@RequestMapping(FakeTossController.BASE)
@ConditionalOnProperty(name = "uplog.toss.fake-server.enabled", havingValue = "true")
public class FakeTossController {

    public static final String BASE = "/__fake-toss";

    private static final LocalDate ACCOUNT1_START = LocalDate.of(2024, 10, 21);
    /** {@code client_id}가 market-blocked로 시작하면 이 그룹은 403(연결 뒤 시세 조회만 막힌 키 흉내) */
    private static final java.util.Set<RateLimitGroup> MARKET_GROUPS = java.util.Set.of(
            RateLimitGroup.STOCK, RateLimitGroup.STOCK_ALL, RateLimitGroup.MARKET_DATA_CHART, RateLimitGroup.MARKET_INFO);
    private static final java.util.Set<String> LISTING_MARKETS = java.util.Set.of("KOSPI", "KOSDAQ", "NYSE", "NASDAQ", "AMEX", "KR_ETC", "US_ETC");
    private static final LocalDate ACCOUNT2_START = LocalDate.of(2025, 3, 3);

    private record Book(FakeTossMarket market, Map<String, FakeTossBook> accounts) {}

    private final Clock clock;
    private final double rateLimitMultiplier;
    private final Map<String, String> currentToken = new ConcurrentHashMap<>();
    private final Map<String, String> tokenOwner = new ConcurrentHashMap<>();
    private final Map<String, double[]> buckets = new HashMap<>();
    private volatile Book book;

    @Autowired
    public FakeTossController(
            @Value("${uplog.toss.fake-server.rate-limit-multiplier:1}") double rateLimitMultiplier) {
        this(Clock.system(FakeTossMarket.SEOUL), rateLimitMultiplier);
    }

    FakeTossController(Clock clock, double rateLimitMultiplier) {
        this.clock = clock;
        this.rateLimitMultiplier = rateLimitMultiplier;
    }

    // ---- 인증

    @PostMapping(value = "/oauth2/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "client_secret", required = false) String clientSecret) {
        if (grantType == null || !grantType.equals("client_credentials")) {
            return oauthError(HttpStatus.BAD_REQUEST, "invalid_request", "OAuth 2.0 Parameter: grant_type");
        }
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return oauthError(HttpStatus.BAD_REQUEST, "invalid_request", "OAuth 2.0 Parameter: client_secret");
        }
        if (clientId.startsWith("ip-blocked")) {
            return oauthError(HttpStatus.FORBIDDEN, "access_denied", "IP address not allowed");
        }
        if (clientId.startsWith("bad-id")) {
            return oauthError(HttpStatus.UNAUTHORIZED, "invalid_client", "Client authentication failed: client_id");
        }
        if (clientSecret.equals("invalid")) {
            return oauthError(HttpStatus.UNAUTHORIZED, "invalid_client", "Client authentication failed: client_secret");
        }
        String token = "fake." + UUID.randomUUID();
        String previous = currentToken.put(clientId, token);
        if (previous != null) {
            tokenOwner.put(previous, "revoked:" + clientId);
        }
        tokenOwner.put(token, clientId);
        Map<String, Object> body = obj("access_token", token, "token_type", "Bearer", "expires_in", 86_400);
        return ResponseEntity.ok().headers(limitHeaders(clientId, RateLimitGroup.AUTH)).body(body);
    }

    // ---- 계좌·자산

    @GetMapping("/api/v1/accounts")
    public ResponseEntity<Map<String, Object>> accounts(@RequestHeader(name = "Authorization", required = false) String auth) {
        return call(auth, RateLimitGroup.ACCOUNT, null, false, (client, account) -> {
            List<Object> accounts = new ArrayList<>();
            if (!client.startsWith("no-account")) {
                accounts.add(obj("accountNo", "1001-23-456789", "accountSeq", 1, "accountType", "BROKERAGE"));
                if (client.startsWith("multi")) {
                    accounts.add(obj("accountNo", "1001-87-654321", "accountSeq", 2, "accountType", "BROKERAGE"));
                }
            }
            return ok(accounts);
        });
    }

    @GetMapping("/api/v1/holdings")
    public ResponseEntity<Map<String, Object>> holdings(
            @RequestHeader(name = "Authorization", required = false) String auth,
            @RequestHeader(name = "X-Tossinvest-Account", required = false) String accountSeq,
            @RequestParam(required = false) String symbol) {
        return call(auth, RateLimitGroup.ASSET, accountSeq, true, (client, account) -> {
            Book b = book();
            BigDecimal fx = b.market().usdKrw(b.market().today()).orElseThrow();
            List<Object> items = new ArrayList<>();
            BigDecimal[] purchase = {BigDecimal.ZERO, BigDecimal.ZERO};
            BigDecimal[] value = {BigDecimal.ZERO, BigDecimal.ZERO};
            BigDecimal[] daily = {BigDecimal.ZERO, BigDecimal.ZERO};
            for (Position p : account.positions()) {
                Symbol s = p.symbol();
                if (symbol != null && !symbol.equals(s.symbol())) {
                    continue;
                }
                var closes = b.market().closes(s.symbol());
                BigDecimal last = closes.lastEntry().getValue();
                BigDecimal prev = closes.lowerEntry(closes.lastKey()).getValue();
                int scale = s.usd() ? 2 : 0;
                BigDecimal purchaseAmount = p.averagePrice().multiply(p.quantity()).setScale(scale, RoundingMode.HALF_UP);
                BigDecimal amount = last.multiply(p.quantity()).setScale(scale, RoundingMode.HALF_UP);
                BigDecimal cost = amount.multiply(new BigDecimal(s.usd() ? "0.001" : "0.00195")).setScale(scale, RoundingMode.HALF_UP);
                BigDecimal profit = amount.subtract(purchaseAmount);
                BigDecimal dailyPnl = last.subtract(prev).multiply(p.quantity()).setScale(scale, RoundingMode.HALF_UP);
                int i = s.usd() ? 1 : 0;
                purchase[i] = purchase[i].add(purchaseAmount);
                value[i] = value[i].add(amount);
                daily[i] = daily[i].add(dailyPnl);
                items.add(obj(
                        "symbol", s.symbol(),
                        "name", s.name(),
                        "marketCountry", s.country(),
                        "currency", s.currency(),
                        "quantity", p.quantity().toPlainString(),
                        "lastPrice", last.toPlainString(),
                        "averagePurchasePrice", p.averagePrice().setScale(s.usd() ? 4 : 2, RoundingMode.HALF_UP).toPlainString(),
                        "marketValue", obj(
                                "purchaseAmount", purchaseAmount.toPlainString(),
                                "amount", amount.toPlainString(),
                                "amountAfterCost", amount.subtract(cost).toPlainString()),
                        "profitLoss", obj(
                                "amount", profit.toPlainString(),
                                "amountAfterCost", profit.subtract(cost).toPlainString(),
                                "rate", ratio(profit, purchaseAmount),
                                "rateAfterCost", ratio(profit.subtract(cost), purchaseAmount)),
                        "dailyProfitLoss", obj(
                                "amount", dailyPnl.toPlainString(),
                                "rate", ratio(last.subtract(prev), prev)),
                        "cost", obj(
                                "commission", amount.multiply(new BigDecimal(s.usd() ? "0.001" : "0.00015"))
                                        .setScale(scale, RoundingMode.HALF_UP).toPlainString(),
                                "tax", s.usd() ? null : amount.multiply(new BigDecimal("0.0018"))
                                        .setScale(0, RoundingMode.DOWN).toPlainString())));
            }
            BigDecimal purchaseKrw = purchase[0].add(purchase[1].multiply(fx));
            BigDecimal profitKrw = value[0].add(value[1].multiply(fx)).subtract(purchaseKrw);
            BigDecimal dailyKrw = daily[0].add(daily[1].multiply(fx));
            return ok(obj(
                    "totalPurchaseAmount", price(purchase),
                    "marketValue", obj("amount", price(value), "amountAfterCost", price(value)),
                    "profitLoss", obj(
                            "amount", price(new BigDecimal[] {value[0].subtract(purchase[0]), value[1].subtract(purchase[1])}),
                            "amountAfterCost", price(new BigDecimal[] {value[0].subtract(purchase[0]), value[1].subtract(purchase[1])}),
                            "rate", ratio(profitKrw, purchaseKrw),
                            "rateAfterCost", ratio(profitKrw, purchaseKrw)),
                    "dailyProfitLoss", obj("amount", price(daily), "rate", ratio(dailyKrw, purchaseKrw)),
                    "items", items));
        });
    }

    @GetMapping("/api/v1/buying-power")
    public ResponseEntity<Map<String, Object>> buyingPower(
            @RequestHeader(name = "Authorization", required = false) String auth,
            @RequestHeader(name = "X-Tossinvest-Account", required = false) String accountSeq,
            @RequestParam String currency) {
        return call(auth, RateLimitGroup.ORDER_INFO, accountSeq, true, (client, account) -> switch (currency) {
            case "KRW" -> ok(obj("currency", "KRW", "cashBuyingPower", account.krw().setScale(0, RoundingMode.DOWN).toPlainString()));
            case "USD" -> ok(obj("currency", "USD", "cashBuyingPower", account.usd().toPlainString()));
            default -> apiError(HttpStatus.BAD_REQUEST, "invalid-request", "지원하지 않는 통화입니다.");
        });
    }

    @GetMapping("/api/v1/orders")
    public ResponseEntity<Map<String, Object>> orders(
            @RequestHeader(name = "Authorization", required = false) String auth,
            @RequestHeader(name = "X-Tossinvest-Account", required = false) String accountSeq,
            @RequestParam String status,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        return call(auth, RateLimitGroup.ORDER_HISTORY, accountSeq, true, (client, account) -> {
            if (!status.equals("OPEN") && !status.equals("CLOSED")) {
                return apiError(HttpStatus.BAD_REQUEST, "invalid-request", "유효하지 않은 주문 상태 필터입니다. OPEN 또는 CLOSED 만 허용됩니다.");
            }
            if (status.equals("OPEN")) {
                return ok(obj("orders", List.of(), "nextCursor", null, "hasNext", false));
            }
            List<Order> matched = account.ordersNewestFirst().stream()
                    .filter(o -> symbol == null || o.symbol().symbol().equals(symbol))
                    .filter(o -> from == null || !o.orderedAt().toLocalDate().isBefore(from))
                    .filter(o -> to == null || !o.orderedAt().toLocalDate().isAfter(to))
                    .toList();
            int size = Math.max(1, Math.min(limit, 100));
            int offset = cursor == null ? 0 : Integer.parseInt(cursor.substring(1));
            List<Object> page = new ArrayList<>();
            for (int i = offset; i < Math.min(matched.size(), offset + size); i++) {
                page.add(order(matched.get(i)));
            }
            boolean hasNext = offset + size < matched.size();
            return ok(obj("orders", page, "nextCursor", hasNext ? "c" + (offset + size) : null, "hasNext", hasNext));
        });
    }

    // ---- 시세·시장 정보

    @GetMapping("/api/v1/exchange-rate")
    public ResponseEntity<Map<String, Object>> exchangeRate(
            @RequestHeader(name = "Authorization", required = false) String auth,
            @RequestParam(required = false) String dateTime,
            @RequestParam String baseCurrency,
            @RequestParam String quoteCurrency) {
        return call(auth, RateLimitGroup.MARKET_INFO, null, false, (client, account) -> {
            if (!baseCurrency.equals("USD") || !quoteCurrency.equals("KRW")) {
                return apiError(HttpStatus.BAD_REQUEST, "invalid-request", "지원하지 않는 통화입니다.");
            }
            OffsetDateTime at;
            try {
                at = dateTime == null ? OffsetDateTime.now(clock) : OffsetDateTime.parse(dateTime);
            } catch (DateTimeParseException e) {
                return apiError(HttpStatus.BAD_REQUEST, "invalid-request", "dateTime 형식이 올바르지 않습니다.");
            }
            LocalDate date = at.atZoneSameInstant(FakeTossMarket.SEOUL).toLocalDate();
            Optional<BigDecimal> mid = book().market().usdKrw(date);
            if (mid.isEmpty() || date.isAfter(book().market().today())) {
                return apiError(HttpStatus.NOT_FOUND, "exchange-rate-not-found", "요청한 시점의 환율 정보가 없습니다.");
            }
            BigDecimal rate = mid.get().multiply(new BigDecimal("1.0175")).setScale(2, RoundingMode.HALF_UP);
            OffsetDateTime from = at.withSecond(0).withNano(0);
            return ok(obj(
                    "baseCurrency", "USD",
                    "quoteCurrency", "KRW",
                    "rate", rate.toPlainString(),
                    "midRate", mid.get().toPlainString(),
                    "basisPoint", rate.subtract(mid.get()).divide(mid.get(), MathContext.DECIMAL64)
                            .multiply(BigDecimal.valueOf(10_000)).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    "rateChangeType", "EQUAL",
                    "validFrom", from.toString(),
                    "validUntil", from.plusMinutes(1).toString()));
        });
    }

    @GetMapping("/api/v1/candles")
    public ResponseEntity<Map<String, Object>> candles(
            @RequestHeader(name = "Authorization", required = false) String auth,
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam(required = false, defaultValue = "100") int count,
            @RequestParam(required = false) String before,
            @RequestParam(required = false, defaultValue = "true") boolean adjusted) {
        return call(auth, RateLimitGroup.MARKET_DATA_CHART, null, false, (client, account) -> {
            Symbol s = FakeTossMarket.BY_CODE.get(symbol);
            if (s == null) {
                return apiError(HttpStatus.NOT_FOUND, "stock-not-found", "종목을 찾을 수 없습니다.");
            }
            if (!interval.equals("1d")) {
                return apiError(HttpStatus.BAD_REQUEST, "invalid-request", "지원하지 않는 캔들 주기입니다.");
            }
            var closes = book().market().closes(symbol);
            LocalDate upTo = before == null ? closes.lastKey()
                    : OffsetDateTime.parse(before).atZoneSameInstant(s.zone()).toLocalDate();
            List<Object> page = new ArrayList<>();
            LocalDate oldest = null;
            for (var e : closes.headMap(upTo, true).descendingMap().entrySet()) {
                if (page.size() >= Math.max(1, Math.min(count, 200))) {
                    break;
                }
                BigDecimal c = e.getValue();
                int scale = s.usd() ? 2 : 0;
                page.add(obj(
                        "timestamp", e.getKey().atStartOfDay(s.zone()).toOffsetDateTime().toString(),
                        "openPrice", c.multiply(new BigDecimal("0.998")).setScale(scale, RoundingMode.HALF_UP).toPlainString(),
                        "highPrice", c.multiply(new BigDecimal("1.012")).setScale(scale, RoundingMode.HALF_UP).toPlainString(),
                        "lowPrice", c.multiply(new BigDecimal("0.988")).setScale(scale, RoundingMode.HALF_UP).toPlainString(),
                        "closePrice", c.toPlainString(),
                        "volume", String.valueOf(100_000 + Math.abs(e.getKey().hashCode() % 900_000)),
                        "currency", s.currency()));
                oldest = e.getKey();
            }
            boolean more = oldest != null && closes.lowerKey(oldest) != null;
            String nextBefore = more ? oldest.minusDays(1).atStartOfDay(s.zone()).toOffsetDateTime().toString() : null;
            return ok(obj("candles", page, "nextBefore", nextBefore));
        });
    }

    @GetMapping("/api/v1/stocks/all")
    public ResponseEntity<Map<String, Object>> listStocks(
            @RequestHeader(name = "Authorization", required = false) String auth,
            @RequestParam(required = false) String market,
            @RequestParam(required = false, defaultValue = "ACTIVE") String status,
            @RequestParam(required = false) String securityType,
            @RequestParam(required = false) Boolean commonShare) {
        return call(auth, RateLimitGroup.STOCK_ALL, null, false, (client, account) -> {
            if (market == null || !LISTING_MARKETS.contains(market)) {
                return apiError(HttpStatus.BAD_REQUEST, "invalid-request",
                        "유효하지 않은 market 입니다. KOSPI, KOSDAQ, NYSE, NASDAQ, AMEX, KR_ETC, US_ETC 만 허용됩니다.");
            }
            List<Object> stocks = new ArrayList<>();
            if (!status.equals("ACTIVE")) {
                return ok(stocks);
            }
            FakeTossMarket.UNIVERSE.stream()
                    .filter(s -> s.exchange().equals(market))
                    .filter(s -> securityType == null || s.securityType().equals(securityType))
                    .filter(s -> commonShare == null || s.commonShare() == commonShare)
                    .sorted(java.util.Comparator.comparing(Symbol::symbol))
                    .forEach(s -> stocks.add(obj(
                            "symbol", s.symbol(),
                            "name", s.name(),
                            "securityType", s.securityType(),
                            "isCommonShare", s.commonShare(),
                            "isinCode", s.isin())));
            return ok(stocks);
        });
    }

    @GetMapping("/api/v1/stocks")
    public ResponseEntity<Map<String, Object>> stocks(
            @RequestHeader(name = "Authorization", required = false) String auth,
            @RequestParam(required = false) String symbols) {
        return call(auth, RateLimitGroup.STOCK, null, false, (client, account) -> {
            if (symbols == null || !symbols.matches("[A-Za-z0-9.,\\-]+") || symbols.split(",").length > 200) {
                return apiError(HttpStatus.BAD_REQUEST, "invalid-request", "symbols 형식이 올바르지 않습니다.");
            }
            List<Object> stocks = new ArrayList<>();
            for (String code : symbols.split(",")) {
                Symbol s = FakeTossMarket.BY_CODE.get(code);
                if (s == null) {
                    continue;
                }
                boolean kr = s.country().equals("KR");
                LocalDate listDate = s.listDate() != null ? s.listDate() : LocalDate.of(kr ? 2002 : 1993, 1, 29);
                stocks.add(obj(
                        "symbol", s.symbol(),
                        "name", s.name(),
                        "englishName", s.symbol(),
                        "isinCode", s.isin(),
                        "market", s.exchange(),
                        "securityType", s.securityType(),
                        "isCommonShare", s.commonShare(),
                        "status", "ACTIVE",
                        "currency", s.currency(),
                        "listDate", listDate.toString(),
                        "delistDate", null,
                        "sharesOutstanding", "100000000",
                        "leverageFactor", null,
                        "koreanMarketDetail", kr ? obj("liquidationTrading", false, "nxtSupported", true,
                                "krxTradingSuspended", false, "nxtTradingSuspended", false) : null));
            }
            if (stocks.isEmpty()) {
                return apiError(HttpStatus.NOT_FOUND, "stock-not-found", "종목을 찾을 수 없습니다.");
            }
            return ok(stocks);
        });
    }

    @GetMapping("/api/v1/market-calendar/{market}")
    public ResponseEntity<Map<String, Object>> marketCalendar(
            @RequestHeader(name = "Authorization", required = false) String auth,
            @PathVariable String market,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return call(auth, RateLimitGroup.MARKET_INFO, null, false, (client, account) -> {
            LocalDate today = date != null ? date : LocalDate.now(clock.withZone(FakeTossMarket.SEOUL));
            LocalDate prev = today.minusDays(1);
            while (!FakeTossMarket.weekday(prev)) {
                prev = prev.minusDays(1);
            }
            LocalDate next = today.plusDays(1);
            while (!FakeTossMarket.weekday(next)) {
                next = next.plusDays(1);
            }
            return switch (market) {
                case "KR" -> ok(obj("today", krDay(today), "previousBusinessDay", krDay(prev), "nextBusinessDay", krDay(next)));
                case "US" -> ok(obj("today", usDay(today), "previousBusinessDay", usDay(prev), "nextBusinessDay", usDay(next)));
                default -> apiError(HttpStatus.NOT_FOUND, "not-found", "지원하지 않는 시장입니다.");
            };
        });
    }

    // ---- 공통

    @FunctionalInterface
    private interface Handler {
        ResponseEntity<Map<String, Object>> handle(String clientId, FakeTossBook account);
    }

    private ResponseEntity<Map<String, Object>> call(
            String authorization, RateLimitGroup group, String accountSeq, boolean accountRequired, Handler handler) {
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
        String owner = token == null ? null : tokenOwner.get(token);
        if (owner == null) {
            return apiError(HttpStatus.UNAUTHORIZED, "invalid-token", "유효하지 않은 토큰입니다.");
        }
        if (owner.startsWith("revoked:")) {
            return apiError(HttpStatus.UNAUTHORIZED, "token-revoked",
                    "새로 발급된 토큰으로 대체되어 더 이상 유효하지 않은 토큰입니다. 최신 토큰으로 다시 시도해 주세요.");
        }
        if (owner.startsWith("market-blocked") && MARKET_GROUPS.contains(group)) {
            return apiError(HttpStatus.FORBIDDEN, "ip-not-allowed", "허용되지 않은 IP 입니다.");
        }
        HttpHeaders headers = new HttpHeaders();
        Integer retryAfter = take(owner, group, headers);
        if (retryAfter != null) {
            headers.set("Retry-After", String.valueOf(retryAfter));
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers)
                    .body(errorBody("rate-limit-exceeded", "요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."));
        }
        FakeTossBook account = null;
        if (accountRequired) {
            if (accountSeq == null || accountSeq.isBlank()) {
                return apiError(HttpStatus.BAD_REQUEST, "account-header-required", "x-tossinvest-account 헤더가 필요합니다.");
            }
            account = owner.startsWith("no-account") ? null : book().accounts().get(owner.startsWith("multi") ? accountSeq
                    : accountSeq.equals("1") ? "1" : "none");
            if (account == null) {
                return apiError(HttpStatus.NOT_FOUND, "account-not-found", "계좌를 찾을 수 없습니다.");
            }
        }
        ResponseEntity<Map<String, Object>> response = handler.handle(owner, account);
        return ResponseEntity.status(response.getStatusCode()).headers(headers).body(response.getBody());
    }

    /** 그룹 버킷에서 하나를 뗀다. 한도를 넘으면 Retry-After 초. */
    private Integer take(String clientId, RateLimitGroup group, HttpHeaders headers) {
        double rate = group.permitsPerSecond(LocalTime.now(clock.withZone(FakeTossMarket.SEOUL))) * rateLimitMultiplier;
        double capacity = Math.max(1, rate);
        long now = System.nanoTime();
        synchronized (buckets) {
            double[] b = buckets.computeIfAbsent(clientId + "/" + group, k -> new double[] {capacity, now});
            b[0] = Math.min(capacity, b[0] + (now - (long) b[1]) * rate / 1e9);
            b[1] = now;
            headers.set("X-RateLimit-Limit", String.valueOf((int) capacity));
            headers.set("X-RateLimit-Reset", "1");
            if (b[0] < 1) {
                headers.set("X-RateLimit-Remaining", "0");
                return 1;
            }
            b[0] -= 1;
            headers.set("X-RateLimit-Remaining", String.valueOf((int) Math.floor(b[0])));
            return null;
        }
    }

    private HttpHeaders limitHeaders(String clientId, RateLimitGroup group) {
        HttpHeaders headers = new HttpHeaders();
        take(clientId, group, headers);
        return headers;
    }

    private Book book() {
        LocalDate today = LocalDate.now(clock.withZone(FakeTossMarket.SEOUL));
        Book b = book;
        if (b == null || !b.market().today().equals(today)) {
            FakeTossMarket market = new FakeTossMarket(today);
            List<Symbol> all = FakeTossMarket.SYMBOLS;
            Map<String, FakeTossBook> accounts = new LinkedHashMap<>();
            accounts.put("1", new FakeTossBook(market, 42, ACCOUNT1_START, all));
            accounts.put("2", new FakeTossBook(market, 7, ACCOUNT2_START, List.of(all.get(0), all.get(3), all.get(4), all.get(7))));
            b = new Book(market, accounts);
            book = b;
        }
        return b;
    }

    private static Map<String, Object> order(Order o) {
        boolean filled = o.filledQuantity().signum() > 0;
        return obj(
                "orderId", o.orderId(),
                "symbol", o.symbol().symbol(),
                "side", o.side(),
                "orderType", "LIMIT",
                "timeInForce", "DAY",
                "status", o.status(),
                "price", o.price().toPlainString(),
                "quantity", o.quantity().toPlainString(),
                "orderAmount", null,
                "currency", o.symbol().currency(),
                "orderedAt", o.orderedAt().toString(),
                "canceledAt", o.canceledAt() == null ? null : o.canceledAt().toString(),
                "execution", obj(
                        "filledQuantity", o.filledQuantity().toPlainString(),
                        "averageFilledPrice", filled ? o.averageFilledPrice().toPlainString() : null,
                        "filledAmount", filled ? o.filledAmount().toPlainString() : null,
                        "commission", filled ? o.commission().toPlainString() : null,
                        "tax", o.tax() == null ? null : o.tax().toPlainString(),
                        "filledAt", filled ? o.filledAt().toString() : null,
                        "settlementDate", o.settlementDate() == null ? null : o.settlementDate().toString()));
    }

    private static Map<String, Object> krDay(LocalDate d) {
        if (!FakeTossMarket.weekday(d)) {
            return obj("date", d.toString(), "integrated", null);
        }
        ZoneId z = FakeTossMarket.SEOUL;
        return obj("date", d.toString(), "integrated", obj(
                "preMarket", session(d, z, 8, 0, 8, 50),
                "regularMarket", obj(
                        "startTime", d.atTime(9, 0).atZone(z).toOffsetDateTime().toString(),
                        "singlePriceAuctionStartTime", d.atTime(15, 20).atZone(z).toOffsetDateTime().toString(),
                        "endTime", d.atTime(15, 30).atZone(z).toOffsetDateTime().toString()),
                "afterMarket", session(d, z, 15, 30, 20, 0)));
    }

    private static Map<String, Object> usDay(LocalDate d) {
        if (!FakeTossMarket.weekday(d)) {
            return obj("date", d.toString(), "dayMarket", null, "preMarket", null, "regularMarket", null, "afterMarket", null);
        }
        ZoneId ny = FakeTossMarket.NEW_YORK;
        return obj(
                "date", d.toString(),
                "dayMarket", kst(session(d, ny, -6, -30, 4, 0)),
                "preMarket", kst(session(d, ny, 4, 0, 9, 30)),
                "regularMarket", kst(session(d, ny, 9, 30, 16, 0)),
                "afterMarket", kst(session(d, ny, 16, 0, 20, 0)));
    }

    private static Map<String, Object> session(LocalDate d, ZoneId z, int h1, int m1, int h2, int m2) {
        var start = d.atStartOfDay(z).plusHours(h1).plusMinutes(m1);
        var end = d.atStartOfDay(z).plusHours(h2).plusMinutes(m2);
        return obj("startTime", start.toOffsetDateTime().toString(), "endTime", end.toOffsetDateTime().toString());
    }

    /** 미국 세션 시각은 KST로 준다. */
    private static Map<String, Object> kst(Map<String, Object> session) {
        Map<String, Object> out = new LinkedHashMap<>();
        session.forEach((k, v) -> out.put(k,
                OffsetDateTime.parse((String) v).atZoneSameInstant(FakeTossMarket.SEOUL).toOffsetDateTime().toString()));
        return out;
    }

    private static Map<String, Object> price(BigDecimal[] krwUsd) {
        return obj("krw", krwUsd[0].setScale(0, RoundingMode.HALF_UP).toPlainString(),
                "usd", krwUsd[1].signum() == 0 ? null : krwUsd[1].setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private static String ratio(BigDecimal a, BigDecimal b) {
        return b.signum() == 0 ? "0" : a.divide(b, 4, RoundingMode.HALF_UP).toPlainString();
    }

    private static ResponseEntity<Map<String, Object>> ok(Object result) {
        return ResponseEntity.ok(obj("result", result));
    }

    private static ResponseEntity<Map<String, Object>> apiError(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(errorBody(code, message));
    }

    private static Map<String, Object> errorBody(String code, String message) {
        return obj("error", obj("requestId", "FAKE" + Long.toHexString(System.nanoTime()).toUpperCase(Locale.ROOT),
                "code", code, "message", message));
    }

    private static ResponseEntity<Map<String, Object>> oauthError(HttpStatus status, String error, String description) {
        return ResponseEntity.status(status).body(obj("error", error, "error_description", description));
    }

    /** null 값을 허용하는 순서 있는 맵. */
    static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
