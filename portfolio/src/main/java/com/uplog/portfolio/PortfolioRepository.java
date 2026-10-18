package com.uplog.portfolio;

import com.uplog.calc.Trade;
import com.uplog.calc.portfolio.AccountFlow;
import com.uplog.calc.portfolio.Dividend;
import com.uplog.calc.portfolio.Execution;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * account·trade·cash_flow·dividend·collection_gap·account_day 접근. 원시 테이블은 넣고 읽기만 한다(append-only).
 * 체결·배당의 종목은 instrument.code로 주고받는다.
 */
@Component
public class PortfolioRepository {

    /** ACTIVE: 연결돼 수집 중, DISCONNECTED: 연결 해제(키 파기, 기록은 남김) */
    public enum AccountStatus { ACTIVE, DISCONNECTED }

    /**
     * @param connectedAt 수집을 시작한 날
     * @param userId 주인. 데모 계좌는 null
     * @param brokerAccountSeq 토스 X-Tossinvest-Account 값. 데모 계좌는 null
     * @param maskedAccountNo 화면용 가린 계좌번호. 데모 계좌는 null
     */
    public record Account(
            long id, String name, String broker, boolean sample, LocalDate connectedAt, Long userId, String brokerAccountSeq,
            String maskedAccountNo, AccountStatus status) {}

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

    private static final String ACCOUNT_COLUMNS =
            "id, name, broker, sample, connected_at, user_id, broker_account_seq, masked_account_no, status";

    private static Account account(ResultSet rs) throws SQLException {
        // wasNull()은 마지막으로 읽은 열 기준이라, NULL을 그대로 받으려면 getObject로 읽는다(데모 계좌는 user_id가 NULL).
        Long userId = rs.getObject("user_id", Long.class);
        return new Account(
                rs.getLong("id"), rs.getString("name"), rs.getString("broker"), rs.getBoolean("sample"),
                rs.getDate("connected_at").toLocalDate(), userId, rs.getString("broker_account_seq"),
                rs.getString("masked_account_no"), AccountStatus.valueOf(rs.getString("status")));
    }

    /** 비로그인 방문자가 보는 데모 계좌(주인 없는 sample 계좌 중 가장 먼저 만든 것). */
    public Optional<Account> demoAccount() {
        return jdbc.sql("SELECT " + ACCOUNT_COLUMNS + " FROM account WHERE sample = TRUE AND user_id IS NULL ORDER BY id LIMIT 1")
                .query((rs, n) -> account(rs))
                .optional();
    }

    /** 사용자의 ACTIVE 계좌(사용자당 하나). */
    public Optional<Account> activeAccountOf(long userId) {
        return jdbc.sql("SELECT " + ACCOUNT_COLUMNS + " FROM account WHERE user_id = :userId AND status = 'ACTIVE' ORDER BY id LIMIT 1")
                .param("userId", userId)
                .query((rs, n) -> account(rs))
                .optional();
    }

    /** 사용자의 가장 최근 계좌(상태 무관). */
    public Optional<Account> latestAccountOf(long userId) {
        return jdbc.sql("SELECT " + ACCOUNT_COLUMNS + " FROM account WHERE user_id = :userId ORDER BY id DESC LIMIT 1")
                .param("userId", userId)
                .query((rs, n) -> account(rs))
                .optional();
    }

    /** 사용자의 연결 해제된 계좌 중 같은 증권사 계좌(다시 연결할 때 기록을 이어 쓴다). */
    public Optional<Account> disconnectedAccountOf(long userId, String brokerAccountSeq) {
        return jdbc.sql("SELECT " + ACCOUNT_COLUMNS + """
                         FROM account
                        WHERE user_id = :userId AND broker_account_seq = :seq AND status = 'DISCONNECTED'
                        ORDER BY id DESC LIMIT 1
                        """)
                .param("userId", userId)
                .param("seq", brokerAccountSeq)
                .query((rs, n) -> account(rs))
                .optional();
    }

    public Optional<Account> findAccount(long accountId) {
        return jdbc.sql("SELECT " + ACCOUNT_COLUMNS + " FROM account WHERE id = :id")
                .param("id", accountId)
                .query((rs, n) -> account(rs))
                .optional();
    }

    /** 사용자 행을 잠가 같은 사용자의 연결 요청을 줄 세운다(사용자당 ACTIVE 계좌 하나를 지키려고). */
    public void lockUser(long userId) {
        jdbc.sql("SELECT id FROM app_user WHERE id = :userId FOR UPDATE").param("userId", userId).query(Long.class).optional();
    }

    /**
     * 연결한 토스 계좌를 만든다.
     *
     * @param simulated 가짜 토스 서버에서 온 계좌면 true — 실제 계좌로 보이지 않게 샘플 계좌로 만든다
     */
    @Transactional
    public long insertConnectedAccount(
            long userId, String brokerAccountSeq, String maskedAccountNo, LocalDate connectedAt, boolean simulated) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.sql("""
                        INSERT INTO account (name, broker, sample, connected_at, user_id, broker_account_seq, masked_account_no, status)
                        VALUES (:name, 'TOSS', :sample, :connectedAt, :userId, :seq, :masked, 'ACTIVE')
                        """)
                .param("name", simulated ? "가짜 토스 계좌(개발용)" : "토스증권 계좌")
                .param("sample", simulated)
                .param("connectedAt", Date.valueOf(connectedAt))
                .param("userId", userId)
                .param("seq", brokerAccountSeq)
                .param("masked", maskedAccountNo)
                .update(key, "id");
        return key.getKeyAs(Long.class);
    }

    public void updateStatus(long accountId, AccountStatus status) {
        jdbc.sql("UPDATE account SET status = :status WHERE id = :id")
                .param("status", status.name())
                .param("id", accountId)
                .update();
    }

    public void updateMaskedAccountNo(long accountId, String maskedAccountNo) {
        jdbc.sql("UPDATE account SET masked_account_no = :masked WHERE id = :id")
                .param("masked", maskedAccountNo)
                .param("id", accountId)
                .update();
    }

    /** 계좌의 원시·파생 기록과 계좌 행을 지운다(이 모듈의 테이블만. 다른 모듈은 {@code AccountDataPurger}). */
    @Transactional
    public void deleteAccountData(long accountId) {
        for (String table : new String[] {"account_day", "collection_gap", "dividend", "cash_flow", "trade"}) {
            jdbc.sql("DELETE FROM " + table + " WHERE account_id = :id").param("id", accountId).update();
        }
        jdbc.sql("DELETE FROM account WHERE id = :id").param("id", accountId).update();
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
                        ORDER BY t.date, CASE WHEN t.side = 'BUY' THEN 0 ELSE 1 END, t.id
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
