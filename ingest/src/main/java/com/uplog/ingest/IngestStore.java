package com.uplog.ingest;

import com.uplog.calc.Trade;
import com.uplog.core.CurrencyCode;
import com.uplog.portfolio.broker.BrokerPort;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 수집이 쓰는 계좌 테이블 접근: account(연결 컬럼), trade, cash_flow(INFERRED), collection_gap, holding_snapshot,
 * cash_snapshot, sync_cursor. 체결은 증권사 주문 id(broker_trade_id)로 중복을 거른다.
 */
public class IngestStore {

    /** 추정 입출금의 broker_flow_id 접두어. 일일 수집은 날짜를 붙이고, 역산 구간 추정은 BACKFILL_PREFIX를 쓴다. */
    static final String DAILY_PREFIX = "inferred-";
    static final String BACKFILL_PREFIX = "inferred-backfill-";

    /** 수집 대상 계좌. accountSeq는 토스 X-Tossinvest-Account 값. */
    public record Target(long accountId, String accountSeq, LocalDate connectedAt, String status, boolean sample) {}

    /** 저장할 체결. 가격·수수료·세금은 현지 통화, fxRate는 체결 시점 USD/KRW(원화 종목은 1). */
    public record NewTrade(
            String brokerTradeId, LocalDate date, String symbol, Trade.Side side, BigDecimal quantity, BigDecimal price,
            BigDecimal fxRate, BigDecimal fee, BigDecimal tax) {}

    /** @param fx 그날 USD/KRW */
    public record CashSnapshot(LocalDate date, BigDecimal krw, BigDecimal usd, BigDecimal fx) {

        public BigDecimal totalKrw() {
            return krw.add(usd.multiply(fx));
        }
    }

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public IngestStore(JdbcClient jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    public Optional<Target> target(long accountId) {
        return jdbc.sql("SELECT id, broker_account_seq, connected_at, status, sample FROM account WHERE id = :id")
                .param("id", accountId)
                .query((rs, n) -> new Target(rs.getLong("id"), rs.getString("broker_account_seq"),
                        rs.getDate("connected_at").toLocalDate(), rs.getString("status"), rs.getBoolean("sample")))
                .optional();
    }

    /** 매일 수집할 계좌: 연결돼 있고(ACTIVE) 증권사 계좌가 정해진 실제 계좌. */
    public List<Long> activeAccounts() {
        return jdbc.sql("""
                        SELECT id FROM account
                        WHERE status = 'ACTIVE' AND broker_account_seq IS NOT NULL AND sample = FALSE
                        ORDER BY id
                        """)
                .query(Long.class)
                .list();
    }

    /** 이미 있는 주문 id는 건너뛴다. 같은 날 체결은 넣는 순서(체결 시각순)대로 리플레이된다. @return 새로 넣은 건수 */
    public int insertTrades(long accountId, List<NewTrade> trades) {
        Integer inserted = tx.execute(status -> {
            int n = 0;
            for (NewTrade t : trades) {
                n += jdbc.sql("""
                                INSERT INTO trade (account_id, broker_trade_id, date, instrument_id, side, quantity, price, fx_rate, fee, tax)
                                SELECT :accountId, :brokerId, :date, i.id, :side, :quantity, :price, :fx, :fee, :tax
                                FROM instrument i
                                WHERE i.code = :code
                                  AND NOT EXISTS (SELECT 1 FROM trade t WHERE t.account_id = :accountId AND t.broker_trade_id = :brokerId)
                                """)
                        .param("accountId", accountId)
                        .param("brokerId", t.brokerTradeId())
                        .param("date", Date.valueOf(t.date()))
                        .param("side", t.side().name())
                        .param("quantity", t.quantity())
                        .param("price", t.price())
                        .param("fx", t.fxRate())
                        .param("fee", t.fee())
                        .param("tax", t.tax())
                        .param("code", t.symbol())
                        .update();
            }
            return n;
        });
        return inserted == null ? 0 : inserted;
    }

    public List<String> tradedSymbols(long accountId) {
        return jdbc.sql("""
                        SELECT DISTINCT i.code FROM trade t JOIN instrument i ON i.id = t.instrument_id
                        WHERE t.account_id = :accountId ORDER BY i.code
                        """)
                .param("accountId", accountId)
                .query(String.class)
                .list();
    }

    /** 종목별 첫 체결일. */
    public Optional<LocalDate> firstTradeDate(long accountId, String symbol) {
        return jdbc.sql("""
                        SELECT MIN(t.date) FROM trade t JOIN instrument i ON i.id = t.instrument_id
                        WHERE t.account_id = :accountId AND i.code = :code
                        """)
                .param("accountId", accountId)
                .param("code", symbol)
                .query((rs, n) -> Optional.ofNullable(rs.getDate(1)).map(Date::toLocalDate))
                .single();
    }

    public Optional<LocalDate> firstUsdTradeDate(long accountId) {
        return jdbc.sql("""
                        SELECT MIN(t.date) FROM trade t JOIN instrument i ON i.id = t.instrument_id
                        WHERE t.account_id = :accountId AND i.currency = 'USD'
                        """)
                .param("accountId", accountId)
                .query((rs, n) -> Optional.ofNullable(rs.getDate(1)).map(Date::toLocalDate))
                .single();
    }

    public long tradeCount(long accountId) {
        return jdbc.sql("SELECT COUNT(*) FROM trade WHERE account_id = :accountId")
                .param("accountId", accountId)
                .query(Long.class)
                .single();
    }

    /** 그날 보유 스냅샷을 통째로 바꾼다. */
    public void replaceHoldings(long accountId, LocalDate date, List<BrokerPort.Holding> holdings) {
        tx.executeWithoutResult(status -> {
            jdbc.sql("DELETE FROM holding_snapshot WHERE account_id = :accountId AND date = :date")
                    .param("accountId", accountId)
                    .param("date", Date.valueOf(date))
                    .update();
            for (BrokerPort.Holding h : holdings) {
                jdbc.sql("""
                                INSERT INTO holding_snapshot (account_id, date, symbol, quantity, avg_price, last_price, currency)
                                VALUES (:accountId, :date, :symbol, :quantity, :avg, :last, :currency)
                                """)
                        .param("accountId", accountId)
                        .param("date", Date.valueOf(date))
                        .param("symbol", h.symbol())
                        .param("quantity", h.quantity())
                        .param("avg", h.averagePrice() == null ? BigDecimal.ZERO : h.averagePrice())
                        .param("last", h.lastPrice() == null ? BigDecimal.ZERO : h.lastPrice())
                        .param("currency", h.currency().name())
                        .update();
            }
        });
    }

    public void upsertCash(long accountId, CashSnapshot cash) {
        tx.executeWithoutResult(status -> {
            jdbc.sql("DELETE FROM cash_snapshot WHERE account_id = :accountId AND date = :date")
                    .param("accountId", accountId)
                    .param("date", Date.valueOf(cash.date()))
                    .update();
            jdbc.sql("INSERT INTO cash_snapshot (account_id, date, krw, usd, fx) VALUES (:accountId, :date, :krw, :usd, :fx)")
                    .param("accountId", accountId)
                    .param("date", Date.valueOf(cash.date()))
                    .param("krw", cash.krw())
                    .param("usd", cash.usd())
                    .param("fx", cash.fx())
                    .update();
        });
    }

    /** date보다 앞선 가장 최근 현금 스냅샷. */
    public Optional<CashSnapshot> cashBefore(long accountId, LocalDate date) {
        return jdbc.sql("""
                        SELECT date, krw, usd, fx FROM cash_snapshot
                        WHERE account_id = :accountId AND date < :date ORDER BY date DESC LIMIT 1
                        """)
                .param("accountId", accountId)
                .param("date", Date.valueOf(date))
                .query((rs, n) -> new CashSnapshot(rs.getDate("date").toLocalDate(), rs.getBigDecimal("krw"),
                        rs.getBigDecimal("usd"), rs.getBigDecimal("fx")))
                .optional();
    }

    /** 체결의 통화별 현금 변화(매도 +, 매수 −, 비용 −)를 계산할 입력. (from, to] 구간. */
    public List<CashFlowInference.TradeCash> tradeCash(long accountId, LocalDate fromExclusive, LocalDate toInclusive) {
        return jdbc.sql("""
                        SELECT i.currency, t.side, t.quantity, t.price, t.fee, t.tax
                        FROM trade t JOIN instrument i ON i.id = t.instrument_id
                        WHERE t.account_id = :accountId AND t.date > :from AND t.date <= :to
                        """)
                .param("accountId", accountId)
                .param("from", Date.valueOf(fromExclusive))
                .param("to", Date.valueOf(toInclusive))
                .query((rs, n) -> new CashFlowInference.TradeCash(
                        CurrencyCode.valueOf(rs.getString("currency")),
                        Trade.Side.valueOf(rs.getString("side")),
                        rs.getBigDecimal("quantity").multiply(rs.getBigDecimal("price")),
                        rs.getBigDecimal("fee"),
                        rs.getBigDecimal("tax")))
                .list();
    }

    /** 그날의 추정 입출금을 바꾼다(다시 수집하면 다시 계산). |amount| < 1원이면 지우기만 한다. */
    public void replaceInferredFlow(long accountId, LocalDate date, BigDecimal signedAmount) {
        String id = DAILY_PREFIX + date;
        tx.executeWithoutResult(status -> {
            jdbc.sql("DELETE FROM cash_flow WHERE account_id = :accountId AND broker_flow_id = :id")
                    .param("accountId", accountId)
                    .param("id", id)
                    .update();
            if (signedAmount.abs().compareTo(BigDecimal.ONE) >= 0) {
                insertFlow(accountId, id, date, signedAmount);
            }
        });
    }

    /** 역산 구간의 추정 입금을 통째로 바꾼다. */
    public void replaceBackfillFlows(long accountId, List<com.uplog.calc.portfolio.AccountFlow> flows) {
        tx.executeWithoutResult(status -> {
            jdbc.sql("DELETE FROM cash_flow WHERE account_id = :accountId AND broker_flow_id LIKE :prefix")
                    .param("accountId", accountId)
                    .param("prefix", BACKFILL_PREFIX + "%")
                    .update();
            for (var f : flows) {
                insertFlow(accountId, BACKFILL_PREFIX + f.date(), f.date(), f.signedAmount());
            }
        });
    }

    private void insertFlow(long accountId, String brokerFlowId, LocalDate date, BigDecimal signedAmount) {
        jdbc.sql("""
                        INSERT INTO cash_flow (account_id, broker_flow_id, date, type, amount, source)
                        VALUES (:accountId, :id, :date, :type, :amount, 'INFERRED')
                        """)
                .param("accountId", accountId)
                .param("id", brokerFlowId)
                .param("date", Date.valueOf(date))
                .param("type", signedAmount.signum() >= 0 ? "DEPOSIT" : "WITHDRAWAL")
                .param("amount", signedAmount.abs().setScale(2, java.math.RoundingMode.HALF_UP))
                .update();
    }

    public Optional<Instant> cursor(long accountId) {
        return jdbc.sql("SELECT last_order_at FROM sync_cursor WHERE account_id = :accountId")
                .param("accountId", accountId)
                .query((rs, n) -> rs.getTimestamp(1).toInstant())
                .optional();
    }

    /** 커서는 앞으로만 간다. */
    public void advanceCursor(long accountId, Instant lastOrderAt) {
        tx.executeWithoutResult(status -> {
            Optional<Instant> current = cursor(accountId);
            if (current.isEmpty()) {
                jdbc.sql("INSERT INTO sync_cursor (account_id, last_order_at) VALUES (:accountId, :at)")
                        .param("accountId", accountId)
                        .param("at", Timestamp.from(lastOrderAt))
                        .update();
            } else if (lastOrderAt.isAfter(current.get())) {
                jdbc.sql("UPDATE sync_cursor SET last_order_at = :at WHERE account_id = :accountId")
                        .param("accountId", accountId)
                        .param("at", Timestamp.from(lastOrderAt))
                        .update();
            }
        });
    }

    public void insertGap(long accountId, LocalDate date, String reason) {
        try {
            jdbc.sql("INSERT INTO collection_gap (account_id, date, reason) VALUES (:accountId, :date, :reason)")
                    .param("accountId", accountId)
                    .param("date", Date.valueOf(date))
                    .param("reason", reason.length() > 200 ? reason.substring(0, 200) : reason)
                    .update();
        } catch (DuplicateKeyException e) {
            // 이미 누락으로 기록된 날
        }
    }

    public void deleteGap(long accountId, LocalDate date) {
        jdbc.sql("DELETE FROM collection_gap WHERE account_id = :accountId AND date = :date")
                .param("accountId", accountId)
                .param("date", Date.valueOf(date))
                .update();
    }

    public List<LocalDate> gapsSince(long accountId, LocalDate since) {
        return jdbc.sql("SELECT date FROM collection_gap WHERE account_id = :accountId AND date >= :since ORDER BY date")
                .param("accountId", accountId)
                .param("since", Date.valueOf(since))
                .query((rs, n) -> rs.getDate(1).toLocalDate())
                .list();
    }
}
