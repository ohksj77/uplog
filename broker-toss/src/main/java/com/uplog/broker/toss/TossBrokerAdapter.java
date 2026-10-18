package com.uplog.broker.toss;

import com.uplog.calc.Trade;
import com.uplog.core.CurrencyCode;
import com.uplog.portfolio.broker.BrokerPort;
import com.uplog.portfolio.connect.BrokerAccount;
import com.uplog.portfolio.connect.BrokerCredentials;
import com.uplog.portfolio.connect.BrokerException;
import com.uplog.portfolio.connect.BrokerException.Reason;
import com.uplog.portfolio.connect.BrokerVerifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.JsonNode;

/**
 * 토스증권 Open API로 {@link BrokerPort}·{@link BrokerVerifier}를 구현한다. 응답 필드 이름은 공개 OpenAPI 명세 1.2.24를 따른다.
 * 숫자는 모두 문자열로 오므로 BigDecimal로 읽는다.
 */
public class TossBrokerAdapter implements BrokerPort, BrokerVerifier {

    static final String BROKERAGE = "BROKERAGE";
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final TossClient client;

    public TossBrokerAdapter(TossClient client) {
        this.client = client;
    }

    // ---- BrokerVerifier

    @Override
    public List<BrokerAccount> verify(BrokerCredentials credentials) {
        List<BrokerAccount> accounts = accounts(credentials);
        if (accounts.isEmpty()) {
            throw new BrokerException(Reason.NO_BROKERAGE_ACCOUNT, "종합매매 계좌가 없습니다");
        }
        return accounts;
    }

    // ---- BrokerPort

    @Override
    public List<BrokerAccount> accounts(BrokerCredentials credentials) {
        JsonNode result = client.get(credentials, null, "/api/v1/accounts", Map.of(), false);
        List<BrokerAccount> accounts = new ArrayList<>();
        for (JsonNode a : result) {
            String type = a.path("accountType").asString("");
            if (BROKERAGE.equals(type)) {
                accounts.add(new BrokerAccount(
                        a.path("accountSeq").asString(), AccountNumbers.mask(a.path("accountNo").asString("")), type));
            }
        }
        return List.copyOf(accounts);
    }

    @Override
    public List<Holding> holdings(AccountRef account) {
        JsonNode result = client.get(account.credentials(), account.accountSeq(), "/api/v1/holdings", Map.of(), false);
        List<Holding> holdings = new ArrayList<>();
        for (JsonNode h : result.path("items")) {
            holdings.add(new Holding(
                    h.path("symbol").asString(),
                    h.path("name").asString(),
                    market(h.path("marketCountry").asString(""), h.path("currency").asString("")),
                    currency(h.path("currency").asString("")),
                    decimal(h, "quantity"),
                    decimal(h, "averagePurchasePrice"),
                    decimal(h, "lastPrice")));
        }
        return List.copyOf(holdings);
    }

    @Override
    public BigDecimal cash(AccountRef account, CurrencyCode currency) {
        JsonNode result = client.get(account.credentials(), account.accountSeq(), "/api/v1/buying-power",
                Map.of("currency", currency.name()), false);
        BigDecimal cash = decimal(result, "cashBuyingPower");
        return cash == null ? BigDecimal.ZERO : cash;
    }

    @Override
    public OrderPage closedOrders(AccountRef account, LocalDate from, LocalDate to, String cursor) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("status", "CLOSED");
        query.put("from", from);
        query.put("to", to);
        query.put("cursor", cursor);
        query.put("limit", 100);
        JsonNode result = client.get(account.credentials(), account.accountSeq(), "/api/v1/orders", query, false);
        List<FilledOrder> orders = new ArrayList<>();
        for (JsonNode o : result.path("orders")) {
            JsonNode ex = o.path("execution");
            BigDecimal filled = decimal(ex, "filledQuantity");
            BigDecimal price = decimal(ex, "averageFilledPrice");
            String filledAt = ex.path("filledAt").asString(null);
            if (filled == null || filled.signum() <= 0 || price == null || filledAt == null) {
                continue;
            }
            String currency = o.path("currency").asString("");
            BigDecimal amount = decimal(ex, "filledAmount");
            orders.add(new FilledOrder(
                    o.path("orderId").asString(),
                    o.path("symbol").asString(),
                    market("", currency),
                    currency(currency),
                    Trade.Side.valueOf(o.path("side").asString()),
                    o.path("status").asString(""),
                    filled,
                    price,
                    amount == null ? filled.multiply(price) : amount,
                    orZero(decimal(ex, "commission")),
                    orZero(decimal(ex, "tax")),
                    OffsetDateTime.parse(o.path("orderedAt").asString(filledAt)),
                    OffsetDateTime.parse(filledAt)));
        }
        boolean hasNext = result.path("hasNext").asBoolean(false);
        String next = result.path("nextCursor").asString(null);
        return new OrderPage(orders, hasNext && next != null && !next.isBlank() ? next : null);
    }

    @Override
    public Optional<BigDecimal> usdKrw(BrokerCredentials credentials, OffsetDateTime at) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("dateTime", DATE_TIME.format(at));
        query.put("baseCurrency", "USD");
        query.put("quoteCurrency", "KRW");
        JsonNode result = client.get(credentials, null, "/api/v1/exchange-rate", query, true);
        if (result == null) {
            return Optional.empty();
        }
        BigDecimal mid = decimal(result, "midRate");
        return Optional.ofNullable(mid != null ? mid : decimal(result, "rate"));
    }

    @Override
    public Optional<CandlePage> dailyCandles(BrokerCredentials credentials, String symbol, OffsetDateTime before) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("symbol", symbol);
        query.put("interval", "1d");
        query.put("count", 200);
        query.put("before", before == null ? null : DATE_TIME.format(before));
        query.put("adjusted", true);
        JsonNode result = client.get(credentials, null, "/api/v1/candles", query, true);
        if (result == null) {
            return Optional.empty();
        }
        List<Candle> candles = new ArrayList<>();
        for (JsonNode c : result.path("candles")) {
            BigDecimal close = decimal(c, "closePrice");
            if (close == null || close.signum() <= 0) {
                continue;
            }
            candles.add(new Candle(
                    OffsetDateTime.parse(c.path("timestamp").asString()).toLocalDate(), close,
                    currency(c.path("currency").asString(""))));
        }
        String next = result.path("nextBefore").asString(null);
        return Optional.of(new CandlePage(candles, next == null || next.isBlank() ? null : OffsetDateTime.parse(next)));
    }

    @Override
    public MarketDays marketDays(BrokerCredentials credentials, Market market, LocalDate date) {
        JsonNode result = client.get(credentials, null, "/api/v1/market-calendar/" + market.name(),
                Map.of("date", date.toString()), false);
        JsonNode today = result.path("today");
        boolean open = market == Market.KR
                ? !today.path("integrated").isNull() && !today.path("integrated").isMissingNode()
                        && !today.path("integrated").path("regularMarket").isNull()
                : !today.path("regularMarket").isNull() && !today.path("regularMarket").isMissingNode();
        return new MarketDays(
                LocalDate.parse(result.path("previousBusinessDay").path("date").asString()),
                LocalDate.parse(today.path("date").asString(date.toString())),
                open,
                LocalDate.parse(result.path("nextBusinessDay").path("date").asString()));
    }

    // ---- 변환

    static BigDecimal decimal(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asString("").strip();
        return s.isEmpty() ? null : new BigDecimal(s);
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static Market market(String marketCountry, String currency) {
        if (marketCountry.equals("US") || (marketCountry.isEmpty() && currency.equals("USD"))) {
            return Market.US;
        }
        return Market.KR;
    }

    private static CurrencyCode currency(String code) {
        return code.equals("USD") ? CurrencyCode.USD : CurrencyCode.KRW;
    }
}
