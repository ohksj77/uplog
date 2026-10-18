package com.uplog.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** local 프로필(H2 + 샘플 시세 + 데모 계좌)로 띄워 docs/api/portfolio.md의 JSON 형태와 숫자 불변식을 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "api"})
class PortfolioApiTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String[] INSTRUMENT = {"code", "name", "market", "currency"};
    private static final String[] TRADE = {"date", "instrument", "side", "quantity", "priceLocal", "fxRate", "amount", "fee", "tax"};
    private static final String[] PERIOD = {"id", "instrument", "open", "close", "days", "returnRate", "profit", "status"};
    private static final String[] SALE = {"id", "date", "instrument", "quantity", "priceLocal", "fxRate", "amount", "realizedProfit",
        "priceNowLocal", "changeSinceSale", "changeAfter", "verdict", "afterEffect", "series"};
    private static final String[] FX_ITEM = {"instrument", "quantity", "avgCostLocal", "avgFxRate", "priceLocal", "fxRate",
        "priceEffect", "fxEffect", "profit"};
    private static final List<String> CODES = List.of("S00001", "S00002", "S00003", "S00004", "SMPT", "SMPC", "SMPS", "SMPE");

    @Autowired
    private MockMvc mvc;

    private JsonNode fetch(String url) throws Exception {
        return JSON.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void 요약은_데모_계좌와_평가금_순입금_손익을_준다() throws Exception {
        JsonNode s = fetch("/api/portfolio/summary");
        assertKeys(s, "account", "asOf", "totalValue", "netDeposits", "profit", "twr", "todayPnl", "todayReturn");
        assertKeys(s.get("account"), "name", "broker", "sample", "firstDate", "lastDate");
        assertThat(s.get("account").get("name").asString()).isEqualTo("데모 계좌");
        assertThat(s.get("account").get("broker").asString()).isEqualTo("TOSS");
        assertThat(s.get("account").get("sample").asBoolean()).isTrue();
        assertIntegral(s, "totalValue", "netDeposits", "profit", "todayPnl");
        assertThat(s.get("profit").asLong()).isEqualTo(s.get("totalValue").asLong() - s.get("netDeposits").asLong());
        assertThat(s.get("totalValue").asLong()).isPositive();
        assertThat(s.get("asOf").asString()).isEqualTo(s.get("account").get("lastDate").asString());
    }

    @Test
    void 로그는_거래일마다_한_칸이고_통계는_칸의_부호와_맞는다() throws Exception {
        JsonNode first = fetch("/api/portfolio/log");
        List<Integer> years = new ArrayList<>();
        first.get("availableYears").forEach(y -> years.add(y.asInt()));
        assertThat(years).hasSizeGreaterThanOrEqualTo(2).isSorted();
        assertThat(first.get("year").asInt()).isEqualTo(years.getLast());
        int missing = 0;
        for (int year : years) {
            JsonNode log = fetch("/api/portfolio/log?year=" + year);
            assertKeys(log, "year", "availableYears", "from", "to", "days", "stats", "streaks");
            assertKeys(log.get("stats"), "gainDays", "lossDays", "flatDays", "totalPnl", "bestDay", "worstDay");
            assertKeys(log.get("streaks"), "gain", "loss");
            assertKeys(log.get("streaks").get("gain"), "longest", "current");
            assertKeys(log.get("streaks").get("loss"), "longest", "current");
            int gain = 0;
            int loss = 0;
            int flat = 0;
            long total = 0;
            long best = Long.MIN_VALUE;
            String prev = "";
            Long prevValue = null;
            for (JsonNode d : log.get("days")) {
                assertKeys(d, "date", "value", "netCashFlow", "pnl", "dailyReturn", "source");
                assertIntegral(d, "value", "netCashFlow", "pnl");
                assertThat(d.get("date").asString()).startsWith(String.valueOf(year)).isGreaterThan(prev);
                prev = d.get("date").asString();
                String source = d.get("source").asString();
                assertThat(source).isIn("COLLECTED", "BACKFILLED", "MISSING");
                long pnl = d.get("pnl").asLong();
                if (source.equals("MISSING")) {
                    missing++;
                    assertThat(d.get("value").asLong()).isZero();
                    assertThat(pnl).isZero();
                    continue;
                }
                if (prevValue != null) { // 반올림 오차 안에서 pnl = value − netCashFlow − 직전 기록일 value
                    assertThat(d.get("value").asLong() - d.get("netCashFlow").asLong() - prevValue - pnl).isBetween(-2L, 2L);
                }
                prevValue = d.get("value").asLong();
                if (pnl > 0) {
                    gain++;
                } else if (pnl < 0) {
                    loss++;
                } else {
                    flat++;
                }
                total += pnl;
                best = Math.max(best, pnl);
            }
            JsonNode stats = log.get("stats");
            assertThat(stats.get("gainDays").asInt()).isEqualTo(gain);
            assertThat(stats.get("lossDays").asInt()).isEqualTo(loss);
            assertThat(stats.get("flatDays").asInt()).isEqualTo(flat);
            assertThat(stats.get("totalPnl").asLong()).isEqualTo(total);
            assertThat(stats.get("bestDay").get("pnl").asLong()).isEqualTo(best);
            assertKeys(stats.get("worstDay"), "date", "pnl");
            assertThat(log.get("from").asString()).isEqualTo(log.get("days").get(0).get("date").asString());
        }
        assertThat(missing).isEqualTo(2);

        mvc.perform(get("/api/portfolio/log").param("year", "1999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")))
                .andExpect(jsonPath("$.message", containsString("year")));
        mvc.perform(get("/api/portfolio/log").param("year", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    void 보유_종목은_평가금액_큰_순이다() throws Exception {
        JsonNode holdings = fetch("/api/portfolio/holdings");
        assertThat(holdings.size()).isGreaterThanOrEqualTo(3);
        long prev = Long.MAX_VALUE;
        double weights = 0;
        for (JsonNode h : holdings) {
            assertKeys(h, "instrument", "quantity", "avgCostLocal", "priceLocal", "fxRate", "value", "profit", "returnRate", "weight");
            assertKeys(h.get("instrument"), INSTRUMENT);
            assertIntegral(h, "value", "profit");
            assertThat(h.get("value").asLong()).isLessThanOrEqualTo(prev);
            prev = h.get("value").asLong();
            weights += h.get("weight").asDouble();
            if (h.get("instrument").get("currency").asString().equals("KRW")) {
                assertThat(h.get("fxRate").asDouble()).isEqualTo(1.0);
                assertThat(h.get("instrument").get("market").asString()).isEqualTo("KR");
            }
        }
        assertThat(weights).isBetween(0.0, 1.0);
    }

    @Test
    void 하루_상세의_종목_기여_배당_비용을_합하면_그날_손익이다() throws Exception {
        JsonNode log = fetch("/api/portfolio/log");
        Set<String> tradeDays = new TreeSet<>();
        for (String code : CODES) {
            fetch("/api/portfolio/holdings/" + code).get("trades").forEach(t -> tradeDays.add(t.get("date").asString()));
        }
        List<String> dates = new ArrayList<>();
        String missingDay = null;
        for (JsonNode d : log.get("days")) {
            if (d.get("source").asString().equals("MISSING")) {
                missingDay = d.get("date").asString();
            } else {
                dates.add(d.get("date").asString());
            }
        }
        Set<String> checked = new TreeSet<>(dates.subList(dates.size() - 15, dates.size())); // 수집 실패 다음 날 포함
        tradeDays.stream().filter(d -> d.startsWith(log.get("year").asString())).forEach(checked::add);
        checked.add(fetch("/api/portfolio/summary").get("account").get("firstDate").asString());

        for (String date : checked) {
            JsonNode day = fetch("/api/portfolio/days/" + date);
            assertKeys(day, "date", "prevDate", "nextDate", "value", "netCashFlow", "pnl", "dailyReturn", "contributions", "trades", "cashFlows");
            assertIntegral(day, "value", "netCashFlow", "pnl");
            long sum = 0;
            for (JsonNode c : day.get("contributions")) {
                assertKeys(c, "instrument", "pnl", "dailyReturn", "weight");
                sum += c.get("pnl").asLong();
            }
            long flows = 0;
            for (JsonNode f : day.get("cashFlows")) {
                assertKeys(f, "type", "amount", "instrument");
                assertThat(f.get("amount").asLong()).isPositive();
                switch (f.get("type").asString()) {
                    case "DIVIDEND" -> {
                        sum += f.get("amount").asLong();
                        assertKeys(f.get("instrument"), INSTRUMENT);
                    }
                    case "DEPOSIT" -> flows += f.get("amount").asLong();
                    case "WITHDRAWAL" -> flows -= f.get("amount").asLong();
                    default -> throw new AssertionError(f);
                }
            }
            for (JsonNode t : day.get("trades")) {
                assertKeys(t, TRADE);
                sum -= t.get("fee").asLong() + t.get("tax").asLong();
            }
            int terms = day.get("contributions").size() + 2 * day.get("trades").size() + day.get("cashFlows").size();
            assertThat(Math.abs(sum - day.get("pnl").asLong())).as(date).isLessThanOrEqualTo(terms + 1L);
            assertThat(flows).isEqualTo(day.get("netCashFlow").asLong());
            JsonNode prev = day.get("prevDate");
            if (!prev.isNull()) {
                assertThat(prev.asString()).isLessThan(date).isNotEqualTo(missingDay);
            }
        }

        mvc.perform(get("/api/portfolio/days/" + missingDay))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("DATE_NOT_FOUND")));
        mvc.perform(get("/api/portfolio/days/2026-10-17")).andExpect(jsonPath("$.code", is("DATE_NOT_FOUND")));
        mvc.perform(get("/api/portfolio/days/2026-13-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")));
    }

    @Test
    void 종목_상세는_보유분_가격_체결_포지션_매도_환율을_준다() throws Exception {
        Set<String> held = new TreeSet<>();
        fetch("/api/portfolio/holdings").forEach(h -> held.add(h.get("instrument").get("code").asString()));
        for (String code : CODES) {
            JsonNode h = fetch("/api/portfolio/holdings/" + code.toLowerCase(Locale.ROOT));
            assertKeys(h, "instrument", "position", "realizedProfit", "dividends", "prices", "trades", "periods", "sales", "fx");
            assertThat(h.get("instrument").get("code").asString()).isEqualTo(code);
            assertIntegral(h, "realizedProfit", "dividends");
            boolean usd = h.get("instrument").get("currency").asString().equals("USD");
            if (held.contains(code)) {
                assertKeys(h.get("position"), "quantity", "avgCostLocal", "priceLocal", "fxRate", "value", "profit", "returnRate");
            } else {
                assertThat(h.get("position").isNull()).isTrue();
            }
            assertThat(h.get("fx").isNull()).isEqualTo(!(usd && held.contains(code)));
            assertThat(h.get("prices").size()).isGreaterThan(60);
            assertThat(h.get("prices").get(0).get("date").asString())
                    .isLessThan(h.get("trades").get(0).get("date").asString());
            assertThat(h.get("periods").size()).isPositive();
            h.get("periods").forEach(p -> assertKeys(p, PERIOD));
            h.get("sales").forEach(s -> assertKeys(s, SALE));
        }
        mvc.perform(get("/api/portfolio/holdings/NOPE99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("INSTRUMENT_NOT_FOUND")));
    }

    @Test
    void 매도_채점_요약은_매도별_판정과_효과의_합이다() throws Exception {
        for (int horizon : List.of(5, 20, 60)) {
            JsonNode r = fetch("/api/portfolio/insights/sales?horizon=" + horizon);
            assertKeys(r, "horizon", "summary", "sales");
            assertKeys(r.get("summary"), "sales", "judged", "good", "winRate", "avoidedLoss", "missedGain");
            int judged = 0;
            int good = 0;
            long avoided = 0;
            long missed = 0;
            String prev = "9999";
            for (JsonNode s : r.get("sales")) {
                assertKeys(s, SALE);
                assertKeys(s.get("changeAfter"), "d5", "d20", "d60");
                assertIntegral(s, "amount", "realizedProfit", "afterEffect");
                assertThat(s.get("date").asString()).isLessThanOrEqualTo(prev); // 최근 매도 먼저
                prev = s.get("date").asString();
                switch (s.get("verdict").asString()) {
                    case "GOOD" -> {
                        judged++;
                        good++;
                        avoided += s.get("afterEffect").asLong();
                        assertThat(s.get("afterEffect").asLong()).isNotNegative();
                    }
                    case "BAD" -> {
                        judged++;
                        missed += Math.abs(s.get("afterEffect").asLong());
                    }
                    case "PENDING" -> {
                        assertThat(s.get("afterEffect").asLong()).isZero();
                        assertThat(s.get("changeAfter").get("d" + horizon).isNull()).isTrue();
                    }
                    default -> throw new AssertionError(s);
                }
                assertThat(s.get("series").get(0).get("date").asString()).isLessThan(s.get("date").asString());
            }
            JsonNode sum = r.get("summary");
            assertThat(sum.get("sales").asInt()).isEqualTo(r.get("sales").size()).isGreaterThan(10);
            assertThat(sum.get("judged").asInt()).isEqualTo(judged);
            assertThat(sum.get("good").asInt()).isEqualTo(good);
            assertThat(sum.get("avoidedLoss").asLong()).isEqualTo(avoided);
            assertThat(sum.get("missedGain").asLong()).isEqualTo(missed);
        }
        mvc.perform(get("/api/portfolio/insights/sales").param("horizon", "7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("horizon")));
    }

    @Test
    void 그림자_계좌는_같은_입출금을_지수에_넣은_결과다() throws Exception {
        JsonNode summary = fetch("/api/portfolio/summary");
        for (String benchmark : List.of("SPX", "KOSPI200")) {
            JsonNode r = fetch("/api/portfolio/insights/shadow?benchmark=" + benchmark);
            assertKeys(r, "benchmarks", "benchmark", "series", "summary");
            assertThat(r.get("benchmark").asString()).isEqualTo(benchmark);
            assertThat(r.get("benchmarks").size()).isEqualTo(2);
            r.get("benchmarks").forEach(b -> assertKeys(b, "code", "name"));
            assertKeys(r.get("summary"), "actual", "shadow", "difference", "actualTwr", "shadowTwr");
            JsonNode first = r.get("series").get(0);
            assertKeys(first, "date", "actual", "shadow", "netDeposits");
            assertThat(first.get("shadow").asLong()).isEqualTo(first.get("netDeposits").asLong());
            JsonNode s = r.get("summary");
            assertThat(s.get("difference").asLong()).isEqualTo(s.get("actual").asLong() - s.get("shadow").asLong());
            assertThat(s.get("actual").asLong()).isEqualTo(summary.get("totalValue").asLong());
            assertThat(s.get("actualTwr").asDouble()).isEqualTo(summary.get("twr").asDouble());
            assertThat(s.get("shadow").asLong()).isPositive();
        }
        mvc.perform(get("/api/portfolio/insights/shadow").param("benchmark", "NASDAQ")).andExpect(status().isBadRequest());
    }

    @Test
    void 폭포수_항목의_합은_기간_손익과_정확히_같다() throws Exception {
        long profit = fetch("/api/portfolio/summary").get("profit").asLong();
        for (String period : List.of("1M", "3M", "YTD", "1Y", "ALL")) {
            JsonNode r = fetch("/api/portfolio/insights/waterfall?period=" + period);
            assertKeys(r, "period", "from", "to", "startValue", "netCashFlow", "endValue", "items", "total");
            long sum = 0;
            List<String> kinds = new ArrayList<>();
            for (JsonNode item : r.get("items")) {
                assertKeys(item, "kind", "label", "instrument", "amount");
                assertIntegral(item, "amount");
                kinds.add(item.get("kind").asString());
                assertThat(item.get("instrument").isNull()).isEqualTo(!item.get("kind").asString().equals("INSTRUMENT"));
                sum += item.get("amount").asLong();
            }
            long total = r.get("total").asLong();
            assertThat(sum).as(period).isEqualTo(total);
            assertThat(total).isEqualTo(r.get("endValue").asLong() - r.get("startValue").asLong() - r.get("netCashFlow").asLong());
            // 0원인 줄은 내 계좌와 상관없으니 빠진다
            for (JsonNode item : r.get("items")) {
                assertThat(item.get("amount").asLong()).as(item.get("label").asString()).isNotZero();
            }
            r.get("items").forEach(item -> {
                if (item.get("kind").asString().equals("OTHER")) {
                    assertThat(item.get("amount").asLong()).isNotZero(); // 0이면 생략
                }
            });
            if (period.equals("ALL")) {
                assertThat(total).isEqualTo(profit);
            }
        }
        mvc.perform(get("/api/portfolio/insights/waterfall").param("period", "2Y")).andExpect(status().isBadRequest());
    }

    @Test
    void 보유_기간은_open_순이고_버킷은_늘_네_개다() throws Exception {
        JsonNode r = fetch("/api/portfolio/insights/holding-periods");
        assertKeys(r, "periods", "buckets");
        String prev = "";
        int open = 0;
        for (JsonNode p : r.get("periods")) {
            assertKeys(p, PERIOD);
            assertThat(p.get("open").asString()).isGreaterThanOrEqualTo(prev);
            prev = p.get("open").asString();
            boolean isOpen = p.get("status").asString().equals("OPEN");
            assertThat(p.get("close").isNull()).isEqualTo(isOpen);
            open += isOpen ? 1 : 0;
        }
        assertThat(open).isPositive();
        List<String> keys = new ArrayList<>();
        int count = 0;
        for (JsonNode b : r.get("buckets")) {
            assertKeys(b, "key", "label", "count", "winRate", "avgReturn", "profit");
            keys.add(b.get("key").asString());
            count += b.get("count").asInt();
        }
        assertThat(keys).containsExactly("SHORT", "SWING", "MID", "LONG");
        assertThat(count).isEqualTo(r.get("periods").size());
    }

    @Test
    void 환율_분해는_주가_효과와_환율_효과의_합이_손익이다() throws Exception {
        JsonNode r = fetch("/api/portfolio/insights/fx");
        assertKeys(r, "asOf", "items", "total");
        assertKeys(r.get("total"), "priceEffect", "fxEffect", "profit");
        assertThat(r.get("items").size()).isPositive();
        for (JsonNode i : r.get("items")) {
            assertKeys(i, FX_ITEM);
            assertThat(i.get("instrument").get("currency").asString()).isEqualTo("USD");
            assertThat(i.get("priceEffect").asLong() + i.get("fxEffect").asLong()).isEqualTo(i.get("profit").asLong());
        }
        JsonNode t = r.get("total");
        assertThat(t.get("priceEffect").asLong() + t.get("fxEffect").asLong()).isEqualTo(t.get("profit").asLong());
    }

    @Test
    void 수중_차트는_0_이하이고_구간은_깊은_순이다() throws Exception {
        JsonNode r = fetch("/api/portfolio/insights/underwater");
        assertKeys(r, "series", "episodes", "current");
        assertKeys(r.get("current"), "drawdown", "peak", "days", "deepestPast", "deeperThanPast");
        for (JsonNode p : r.get("series")) {
            assertKeys(p, "date", "drawdown", "drawdownAmount");
            assertThat(p.get("drawdown").asDouble()).isLessThanOrEqualTo(0.0);
            assertThat(p.get("drawdownAmount").asLong()).isLessThanOrEqualTo(0L);
        }
        double prev = -1.0;
        for (JsonNode e : r.get("episodes")) {
            assertKeys(e, "peak", "trough", "recovered", "depth", "daysToTrough", "daysToRecover");
            assertThat(e.get("depth").asDouble()).isGreaterThanOrEqualTo(prev).isLessThan(0.0);
            prev = e.get("depth").asDouble();
            assertThat(e.get("daysToRecover").isNull()).isEqualTo(e.get("recovered").isNull());
        }
        JsonNode last = r.get("series").get(r.get("series").size() - 1);
        assertThat(r.get("current").get("drawdown").asDouble()).isEqualTo(last.get("drawdown").asDouble());
    }

    @Test
    void 월별_성적표는_해마다_12칸이고_연_손익은_로그의_totalPnl과_같다() throws Exception {
        JsonNode r = fetch("/api/portfolio/insights/monthly");
        assertKeys(r, "years", "best", "worst", "positiveMonths", "negativeMonths");
        List<Integer> available = new ArrayList<>();
        fetch("/api/portfolio/log").get("availableYears").forEach(y -> available.add(y.asInt()));
        List<Integer> years = new ArrayList<>();
        int positive = 0;
        int negative = 0;
        double best = Double.NEGATIVE_INFINITY;
        double worst = Double.POSITIVE_INFINITY;
        for (JsonNode y : r.get("years")) {
            assertKeys(y, "year", "months", "total");
            assertKeys(y.get("total"), "return", "pnl");
            int year = y.get("year").asInt();
            years.add(year);
            assertThat(y.get("months").size()).isEqualTo(12);
            long pnl = 0;
            int month = 1;
            for (JsonNode m : y.get("months")) {
                assertKeys(m, "month", "return", "pnl", "netCashFlow");
                assertThat(m.get("month").asInt()).isEqualTo(month++);
                boolean empty = m.get("return").isNull();
                assertThat(m.get("pnl").isNull()).isEqualTo(empty);
                assertThat(m.get("netCashFlow").isNull()).isEqualTo(empty);
                if (empty) {
                    continue;
                }
                assertIntegral(m, "pnl", "netCashFlow");
                pnl += m.get("pnl").asLong();
                double ret = m.get("return").asDouble();
                positive += ret > 0 ? 1 : 0;
                negative += ret < 0 ? 1 : 0;
                best = Math.max(best, ret);
                worst = Math.min(worst, ret);
            }
            assertIntegral(y.get("total"), "pnl");
            assertThat(y.get("total").get("pnl").asLong()).as("%d", year).isEqualTo(pnl)
                    .isEqualTo(fetch("/api/portfolio/log?year=" + year).get("stats").get("totalPnl").asLong());
        }
        assertThat(years).isEqualTo(available);
        assertThat(r.get("positiveMonths").asInt()).isEqualTo(positive);
        assertThat(r.get("negativeMonths").asInt()).isEqualTo(negative);
        assertThat(positive + negative).isPositive();
        assertKeys(r.get("best"), "year", "month", "return");
        assertKeys(r.get("worst"), "year", "month", "return");
        assertThat(r.get("best").get("return").asDouble()).isEqualTo(best);
        assertThat(r.get("worst").get("return").asDouble()).isEqualTo(worst);
    }

    @Test
    void 매매_습관은_끝난_포지션을_이익과_손실로_나누고_물타기는_포지션에_이어진다() throws Exception {
        JsonNode r = fetch("/api/portfolio/insights/habits");
        assertKeys(r, "holding", "averagingDown", "activity");
        JsonNode holding = r.get("holding");
        assertKeys(holding, "winners", "losers", "ratio", "verdict");
        assertKeys(holding.get("winners"), "count", "avgDays", "medianDays", "avgReturn");
        assertKeys(holding.get("losers"), "count", "avgDays", "medianDays", "avgReturn");
        assertThat(holding.get("verdict").asString()).isIn("HOLD_LOSERS", "CUT_LOSSES", "BALANCED", "INSUFFICIENT");

        Map<String, Double> returns = new HashMap<>();
        int winners = 0;
        int losers = 0;
        for (JsonNode p : fetch("/api/portfolio/insights/holding-periods").get("periods")) {
            returns.put(p.get("id").asString(), p.get("returnRate").asDouble());
            if (p.get("status").asString().equals("CLOSED")) {
                if (p.get("returnRate").asDouble() > 0) {
                    winners++;
                } else {
                    losers++;
                }
            }
        }
        assertThat(holding.get("winners").get("count").asInt()).isEqualTo(winners);
        assertThat(holding.get("losers").get("count").asInt()).isEqualTo(losers);
        assertThat(winners + losers).isPositive();
        assertThat(holding.get("ratio").isNull()).isEqualTo(winners == 0);

        JsonNode avg = r.get("averagingDown");
        assertKeys(avg, "buys", "shareOfBuys", "positions", "positionsWon", "items");
        assertThat(avg.get("items").size()).isEqualTo(avg.get("buys").asInt());
        assertThat(avg.get("shareOfBuys").asDouble()).isBetween(0.0, 1.0);
        Set<String> positions = new TreeSet<>();
        Set<String> won = new TreeSet<>();
        String prev = "9999";
        for (JsonNode i : avg.get("items")) {
            assertKeys(i, "date", "instrument", "priceLocal", "avgCostLocal", "belowAvg", "positionId", "positionReturn");
            assertThat(i.get("date").asString()).isLessThanOrEqualTo(prev); // 최신 순
            prev = i.get("date").asString();
            assertThat(i.get("belowAvg").asDouble()).isNegative();
            assertThat(i.get("priceLocal").asDouble()).isLessThan(i.get("avgCostLocal").asDouble());
            String id = i.get("positionId").asString();
            assertThat(returns).containsKey(id);
            assertThat(i.get("positionReturn").asDouble()).isEqualTo(returns.get(id));
            positions.add(id);
            if (returns.get(id) > 0) {
                won.add(id);
            }
        }
        assertThat(avg.get("positions").asInt()).isEqualTo(positions.size());
        assertThat(avg.get("positionsWon").asInt()).isEqualTo(won.size());

        JsonNode summary = fetch("/api/portfolio/summary").get("account");
        YearMonth month = YearMonth.parse(summary.get("firstDate").asString().substring(0, 7));
        int sells = 0;
        for (JsonNode a : r.get("activity")) {
            assertKeys(a, "month", "buys", "sells", "turnover");
            assertThat(a.get("month").asString()).isEqualTo(month.toString()); // 빠짐없이
            month = month.plusMonths(1);
            assertThat(a.get("turnover").asDouble()).isNotNegative();
            sells += a.get("sells").asInt();
        }
        assertThat(month.minusMonths(1).toString()).isEqualTo(summary.get("lastDate").asString().substring(0, 7));
        assertThat(sells).isEqualTo(fetch("/api/portfolio/insights/sales").get("summary").get("sales").asInt());
    }

    @Test
    void 배당_흐름은_달이_빠짐없고_월_연_종목별_합이_총액과_같다() throws Exception {
        JsonNode r = fetch("/api/portfolio/insights/dividends");
        assertKeys(r, "months", "byYear", "byInstrument", "total", "trailing12m", "trailingYield", "available", "note");
        // 데모 계좌는 배당 기록이 있어 알 수 있다(연결한 토스 계좌는 API가 배당을 주지 않아 false)
        assertThat(r.get("available").asBoolean()).isTrue();
        assertThat(r.get("note").isNull()).isTrue();
        assertIntegral(r, "total", "trailing12m");
        long total = r.get("total").asLong();
        assertThat(total).isPositive();

        JsonNode account = fetch("/api/portfolio/summary").get("account");
        YearMonth month = YearMonth.parse(account.get("firstDate").asString().substring(0, 7));
        long sum = 0;
        for (JsonNode m : r.get("months")) {
            assertKeys(m, "month", "amount", "items");
            assertThat(m.get("month").asString()).isEqualTo(month.toString());
            month = month.plusMonths(1);
            long items = 0;
            for (JsonNode i : m.get("items")) {
                assertKeys(i, "date", "instrument", "amount");
                assertKeys(i.get("instrument"), INSTRUMENT);
                assertThat(i.get("date").asString()).startsWith(m.get("month").asString());
                items += i.get("amount").asLong();
            }
            assertThat(m.get("amount").asLong()).isEqualTo(items);
            sum += m.get("amount").asLong();
        }
        assertThat(month.minusMonths(1).toString()).isEqualTo(account.get("lastDate").asString().substring(0, 7));
        assertThat(sum).isEqualTo(total);

        long byYear = 0;
        int prevYear = 0;
        for (JsonNode y : r.get("byYear")) {
            assertKeys(y, "year", "amount");
            assertThat(y.get("year").asInt()).isGreaterThan(prevYear);
            prevYear = y.get("year").asInt();
            byYear += y.get("amount").asLong();
        }
        assertThat(byYear).isEqualTo(total);

        long byInstrument = 0;
        long prev = Long.MAX_VALUE;
        for (JsonNode i : r.get("byInstrument")) {
            assertKeys(i, "instrument", "amount", "count");
            assertThat(i.get("amount").asLong()).isLessThanOrEqualTo(prev);
            prev = i.get("amount").asLong();
            assertThat(i.get("count").asInt()).isPositive();
            byInstrument += i.get("amount").asLong();
        }
        assertThat(byInstrument).isEqualTo(total);

        long value = fetch("/api/portfolio/summary").get("totalValue").asLong();
        assertThat(r.get("trailing12m").asLong()).isBetween(0L, total);
        assertThat(r.get("trailingYield").asDouble()).isCloseTo((double) r.get("trailing12m").asLong() / value,
                within(1e-6));
        LocalDate last = LocalDate.parse(account.get("lastDate").asString());
        long trailing = 0;
        for (JsonNode m : r.get("months")) {
            for (JsonNode i : m.get("items")) {
                trailing += LocalDate.parse(i.get("date").asString()).isAfter(last.minusMonths(12)) ? i.get("amount").asLong() : 0;
            }
        }
        assertThat(r.get("trailing12m").asLong()).isEqualTo(trailing);
    }

    @Test
    void 숨은_비용은_수수료와_세금의_합이고_월_종목별_합이_그해_총액과_같다() throws Exception {
        JsonNode first = fetch("/api/portfolio/insights/costs");
        List<Integer> years = new ArrayList<>();
        first.get("availableYears").forEach(y -> years.add(y.asInt()));
        assertThat(first.get("year").asInt()).isEqualTo(years.getLast());
        long allFee = 0;
        long allTax = 0;
        for (int year : years) {
            JsonNode r = fetch("/api/portfolio/insights/costs?year=" + year);
            assertKeys(r, "year", "availableYears", "total", "allTime", "profitBeforeCost", "costShare", "months", "byInstrument");
            assertKeys(r.get("total"), "fee", "tax", "cost");
            assertKeys(r.get("allTime"), "fee", "tax", "cost");
            assertIntegral(r, "profitBeforeCost");
            JsonNode total = r.get("total");
            long fee = total.get("fee").asLong();
            long tax = total.get("tax").asLong();
            assertThat(total.get("cost").asLong()).isEqualTo(fee + tax);
            assertThat(r.get("allTime").get("cost").asLong())
                    .isEqualTo(r.get("allTime").get("fee").asLong() + r.get("allTime").get("tax").asLong());
            allFee += fee;
            allTax += tax;

            long monthFee = 0;
            long monthTax = 0;
            String prevMonth = "";
            for (JsonNode m : r.get("months")) {
                assertKeys(m, "month", "fee", "tax");
                assertThat(m.get("month").asString()).startsWith(year + "-").isGreaterThan(prevMonth);
                prevMonth = m.get("month").asString();
                monthFee += m.get("fee").asLong();
                monthTax += m.get("tax").asLong();
            }
            assertThat(monthFee).isEqualTo(fee);
            assertThat(monthTax).isEqualTo(tax);

            long instrumentFee = 0;
            long instrumentTax = 0;
            long prevCost = Long.MAX_VALUE;
            for (JsonNode i : r.get("byInstrument")) {
                assertKeys(i, "instrument", "fee", "tax", "trades");
                long cost = i.get("fee").asLong() + i.get("tax").asLong();
                assertThat(cost).isLessThanOrEqualTo(prevCost);
                prevCost = cost;
                assertThat(i.get("trades").asInt()).isPositive();
                instrumentFee += i.get("fee").asLong();
                instrumentTax += i.get("tax").asLong();
            }
            assertThat(instrumentFee).isEqualTo(fee);
            assertThat(instrumentTax).isEqualTo(tax);

            long pnl = fetch("/api/portfolio/log?year=" + year).get("stats").get("totalPnl").asLong();
            long beforeCost = r.get("profitBeforeCost").asLong();
            assertThat(beforeCost).isEqualTo(pnl + fee + tax);
            if (beforeCost <= 0) {
                assertThat(r.get("costShare").isNull()).isTrue();
            } else {
                assertThat(r.get("costShare").asDouble()).isCloseTo((double) (fee + tax) / beforeCost,
                        within(1e-6));
            }
        }
        assertThat(first.get("allTime").get("fee").asLong()).isEqualTo(allFee);
        assertThat(first.get("allTime").get("tax").asLong()).isEqualTo(allTax);
        assertThat(allFee + allTax).isPositive();

        mvc.perform(get("/api/portfolio/insights/costs").param("year", "1999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_PARAMETER")))
                .andExpect(jsonPath("$.message", containsString(String.valueOf(years.getLast()))));
        mvc.perform(get("/api/portfolio/insights/costs").param("year", "abc")).andExpect(status().isBadRequest());
    }

    static void assertIntegral(JsonNode node, String... fields) {
        for (String f : fields) {
            assertThat(node.get(f).isIntegralNumber()).as(f + " in " + node).isTrue();
        }
    }

    static void assertKeys(JsonNode node, String... keys) {
        Set<String> actual = new TreeSet<>();
        for (Iterator<String> it = node.propertyNames().iterator(); it.hasNext(); ) {
            actual.add(it.next());
        }
        assertThat(actual).as(node.toString().substring(0, Math.min(80, node.toString().length())))
                .containsExactlyInAnyOrder(keys);
    }

    @Test
    void 분석_목록은_화면마다_이_계좌에_해당하는지와_이유를_준다() throws Exception {
        JsonNode r = fetch("/api/portfolio/insights/availability");

        List<String> keys = new ArrayList<>();
        r.forEach(item -> {
            keys.add(item.get("key").asString());
            boolean available = item.get("available").asBoolean();
            assertThat(item.get("reason").isNull()).as(item.get("key").asString()).isEqualTo(available);
        });
        assertThat(keys).containsExactly("sales", "shadow", "waterfall", "holding-periods", "fx", "underwater", "monthly",
                "habits", "dividends", "costs");
        // 데모 계좌는 매도·해외 종목·배당이 모두 있어 전부 해당한다
        r.forEach(item -> assertThat(item.get("available").asBoolean()).as(item.get("key").asString()).isTrue());
    }
}
