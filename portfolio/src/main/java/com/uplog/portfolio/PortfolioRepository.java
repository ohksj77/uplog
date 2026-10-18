package com.uplog.portfolio;

import com.uplog.calc.Trade;
import com.uplog.calc.portfolio.AccountFlow;
import com.uplog.calc.portfolio.Dividend;
import com.uplog.calc.portfolio.Execution;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * account·trade·cash_flow·dividend·collection_gap·account_day 접근. 원시 테이블은 넣고 읽기만 한다(append-only).
 * 체결·배당의 종목은 instrument.code로 주고받는다.
 */
@Component
public class PortfolioRepository {

    /** @param connectedAt 수집을 시작한 날 */
    public record Account(long id, String name, String broker, boolean sample, LocalDate connectedAt) {}

    /** account_day 한 행. */
    public record AccountDayRow(
            LocalDate date, DaySource source, BigDecimal value, BigDecimal netCashFlow, BigDecimal pnl,
            double dailyReturn, double twrIndex) {}

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;

    public PortfolioRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    /** 로그인 전이라 가장 먼저 만든 계좌가 "현재 계좌"다. */
    public Optional<Account> currentAccount() {
        return jdbc.sql("SELECT id, name, broker, sample, connected_at FROM account ORDER BY id LIMIT 1")
                .query((rs, n) -> new Account(
                        rs.getLong("id"), rs.getString("name"), rs.getString("broker"), rs.getBoolean("sample"),
                        rs.getDate("connected_at").toLocalDate()))
                .optional();
    }

    @Transactional
    public long insertAccount(String name, String broker, boolean sample, LocalDate connectedAt) {
        jdbc.sql("INSERT INTO account (name, broker, sample, connected_at) VALUES (:name, :broker, :sample, :connectedAt)")
                .param("name", name)
                .param("broker", broker)
                .param("sample", sample)
                .param("connectedAt", Date.valueOf(connectedAt))
                .update();
        return jdbc.sql("SELECT MAX(id) FROM account").query(Long.class).single();
    }

    /** 같은 날 체결은 넣은 순서(id)대로 읽는다. 종목코드는 instrument에 있어야 한다. */
    @Transactional
    public void insertTrades(long accountId, List<Execution> trades, String idPrefix) {
        for (int i = 0; i < trades.size(); i++) {
            Execution t = trades.get(i);
            jdbc.sql("""
                            INSERT INTO trade (account_id, broker_trade_id, date, instrument_id, side, quantity, price, fx_rate, fee, tax)
                            SELECT :accountId, :brokerId, :date, id, :side, :quantity, :price, :fx, :fee, :tax
                            FROM instrument WHERE code = :code
                            """)
                    .param("accountId", accountId)
                    .param("brokerId", idPrefix + (i + 1))
                    .param("date", Date.valueOf(t.date()))
                    .param("side", t.side().name())
                    .param("quantity", t.quantity())
                    .param("price", t.priceLocal())
                    .param("fx", t.fxRate())
                    .param("fee", t.feeLocal())
                    .param("tax", t.taxLocal())
                    .param("code", t.instrument())
                    .update();
        }
    }

    @Transactional
    public void insertCashFlows(long accountId, List<AccountFlow> flows, String idPrefix) {
        for (int i = 0; i < flows.size(); i++) {
            AccountFlow f = flows.get(i);
            jdbc.sql("""
                            INSERT INTO cash_flow (account_id, broker_flow_id, date, type, amount)
                            VALUES (:accountId, :brokerId, :date, :type, :amount)
                            """)
                    .param("accountId", accountId)
                    .param("brokerId", idPrefix + (i + 1))
                    .param("date", Date.valueOf(f.date()))
                    .param("type", f.type().name())
                    .param("amount", f.amount())
                    .update();
        }
    }

    @Transactional
    public void insertDividends(long accountId, List<Dividend> dividends, String idPrefix) {
        for (int i = 0; i < dividends.size(); i++) {
            Dividend d = dividends.get(i);
            jdbc.sql("""
                            INSERT INTO dividend (account_id, broker_dividend_id, date, instrument_id, amount)
                            SELECT :accountId, :brokerId, :date, id, :amount FROM instrument WHERE code = :code
                            """)
                    .param("accountId", accountId)
                    .param("brokerId", idPrefix + (i + 1))
                    .param("date", Date.valueOf(d.date()))
                    .param("amount", d.amount())
                    .param("code", d.instrument())
                    .update();
        }
    }

    @Transactional
    public void insertGap(long accountId, LocalDate date, String reason) {
        jdbc.sql("INSERT INTO collection_gap (account_id, date, reason) VALUES (:accountId, :date, :reason)")
                .param("accountId", accountId)
                .param("date", Date.valueOf(date))
                .param("reason", reason)
                .update();
    }

    public List<Execution> trades(long accountId) {
        return jdbc.sql("""
                        SELECT t.date, i.code, t.side, t.quantity, t.price, t.fx_rate, t.fee, t.tax
                        FROM trade t JOIN instrument i ON i.id = t.instrument_id
                        WHERE t.account_id = :accountId
                        ORDER BY t.date, t.id
                        """)
                .param("accountId", accountId)
                .query((rs, n) -> new Execution(
                        rs.getDate("date").toLocalDate(),
                        rs.getString("code"),
                        Trade.Side.valueOf(rs.getString("side")),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("price"),
                        rs.getBigDecimal("fx_rate"),
                        rs.getBigDecimal("fee"),
                        rs.getBigDecimal("tax")))
                .list();
    }

    public List<AccountFlow> cashFlows(long accountId) {
        return jdbc.sql("SELECT date, type, amount FROM cash_flow WHERE account_id = :accountId ORDER BY date, id")
                .param("accountId", accountId)
                .query((rs, n) -> new AccountFlow(
                        rs.getDate("date").toLocalDate(), AccountFlow.Type.valueOf(rs.getString("type")), rs.getBigDecimal("amount")))
                .list();
    }

    public List<Dividend> dividends(long accountId) {
        return jdbc.sql("""
                        SELECT d.date, i.code, d.amount
                        FROM dividend d JOIN instrument i ON i.id = d.instrument_id
                        WHERE d.account_id = :accountId
                        ORDER BY d.date, d.id
                        """)
                .param("accountId", accountId)
                .query((rs, n) -> new Dividend(rs.getDate("date").toLocalDate(), rs.getString("code"), rs.getBigDecimal("amount")))
                .list();
    }

    public Set<LocalDate> gaps(long accountId) {
        return new TreeSet<>(jdbc.sql("SELECT date FROM collection_gap WHERE account_id = :accountId")
                .param("accountId", accountId)
                .query((rs, n) -> rs.getDate("date").toLocalDate())
                .list());
    }

    /** 파생 테이블을 이 계산 결과로 통째로 바꾼다. */
    @Transactional
    public void replaceAccountDays(long accountId, List<AccountDayRow> rows, int calcVersion) {
        jdbc.sql("DELETE FROM account_day WHERE account_id = :accountId").param("accountId", accountId).update();
        jdbcTemplate.batchUpdate(
                """
                INSERT INTO account_day (account_id, date, source, total_value, net_cash_flow, pnl, daily_return, twr_index, calc_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                rows,
                500,
                (ps, r) -> {
                    ps.setLong(1, accountId);
                    ps.setDate(2, Date.valueOf(r.date()));
                    ps.setString(3, r.source().name());
                    ps.setBigDecimal(4, r.value());
                    ps.setBigDecimal(5, r.netCashFlow());
                    ps.setBigDecimal(6, r.pnl());
                    ps.setDouble(7, r.dailyReturn());
                    ps.setDouble(8, r.twrIndex());
                    ps.setInt(9, calcVersion);
                });
    }

    public List<AccountDayRow> accountDays(long accountId) {
        return jdbc.sql("""
                        SELECT date, source, total_value, net_cash_flow, pnl, daily_return, twr_index
                        FROM account_day WHERE account_id = :accountId ORDER BY date
                        """)
                .param("accountId", accountId)
                .query((rs, n) -> new AccountDayRow(
                        rs.getDate("date").toLocalDate(), DaySource.valueOf(rs.getString("source")),
                        rs.getBigDecimal("total_value"), rs.getBigDecimal("net_cash_flow"), rs.getBigDecimal("pnl"),
                        rs.getDouble("daily_return"), rs.getDouble("twr_index")))
                .list();
    }
}
