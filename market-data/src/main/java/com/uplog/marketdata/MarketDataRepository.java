package com.uplog.marketdata;

import com.uplog.calc.PriceSeries;
import com.uplog.core.CurrencyCode;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** instrument·daily_price 접근. JPA 없이 JdbcClient로 쓴다. SQL은 PostgreSQL과 H2(PostgreSQL 모드) 공통 문법만 쓴다. */
@Component
public class MarketDataRepository {

    // 종목 목록은 수백 개라 그대로 훑고, 시세 구간은 PK(instrument_id, date) 인덱스로 min/max를 구한다.
    private static final String LISTED_SELECT = """
            SELECT i.id, i.code, i.name, i.market, i.currency, i.type, i.sample,
                   (SELECT MIN(p.date) FROM daily_price p WHERE p.instrument_id = i.id) AS first_date,
                   (SELECT MAX(p.date) FROM daily_price p WHERE p.instrument_id = i.id) AS last_date
            FROM instrument i
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;

    public MarketDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    /**
     * 이름 또는 종목코드 부분 일치(대소문자 무시)로 시세가 있는 ETF를 찾는다. 이름순.
     * query가 비면 이름순 상위 limit개.
     */
    public List<ListedInstrument> searchEtfs(String query, int limit) {
        String pattern = "%" + escapeLike(query == null ? "" : query.strip().toLowerCase(Locale.ROOT)) + "%";
        return jdbc.sql(LISTED_SELECT + """
                        WHERE i.type = 'ETF'
                          AND (LOWER(i.name) LIKE :pattern ESCAPE '\\' OR LOWER(i.code) LIKE :pattern ESCAPE '\\')
                          AND EXISTS (SELECT 1 FROM daily_price p WHERE p.instrument_id = i.id)
                        ORDER BY i.name, i.code
                        LIMIT :limit
                        """)
                .param("pattern", pattern)
                .param("limit", limit)
                .query(MarketDataRepository::mapListed)
                .list();
    }

    public Optional<ListedInstrument> findByCode(String code) {
        return jdbc.sql(LISTED_SELECT + "WHERE i.code = :code")
                .param("code", code)
                .query(MarketDataRepository::mapListed)
                .optional();
    }

    public Optional<PriceRange> priceRange(long instrumentId) {
        return jdbc.sql("SELECT MIN(date) AS first_date, MAX(date) AS last_date FROM daily_price WHERE instrument_id = :id")
                .param("id", instrumentId)
                .query((rs, n) -> range(rs))
                .optional()
                .flatMap(r -> r);
    }

    /** [from, to] 구간의 종가. 구간에 시세가 없으면 empty. */
    public Optional<PriceSeries> loadCloses(long instrumentId, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> closes = new TreeMap<>();
        jdbc.sql("""
                        SELECT date, close FROM daily_price
                        WHERE instrument_id = :id AND date BETWEEN :from AND :to
                        ORDER BY date
                        """)
                .param("id", instrumentId)
                .param("from", Date.valueOf(from))
                .param("to", Date.valueOf(to))
                .query(rs -> {
                    closes.put(rs.getDate("date").toLocalDate(), rs.getBigDecimal("close"));
                });
        return closes.isEmpty() ? Optional.empty() : Optional.of(new PriceSeries(closes));
    }

    /** 코드가 같으면 이름만 갱신하고 id를 돌려준다. */
    @Transactional
    public long upsertInstrument(NewInstrument instrument) {
        Optional<Long> existing = jdbc.sql("SELECT id FROM instrument WHERE code = :code")
                .param("code", instrument.code())
                .query(Long.class)
                .optional();
        if (existing.isPresent()) {
            jdbc.sql("UPDATE instrument SET name = :name WHERE id = :id")
                    .param("name", instrument.name())
                    .param("id", existing.get())
                    .update();
            return existing.get();
        }
        jdbc.sql("""
                        INSERT INTO instrument (code, name, market, currency, type, sample)
                        VALUES (:code, :name, :market, :currency, :type, :sample)
                        """)
                .param("code", instrument.code())
                .param("name", instrument.name())
                .param("market", instrument.market())
                .param("currency", instrument.currency().name())
                .param("type", instrument.type().name())
                .param("sample", instrument.sample())
                .update();
        return jdbc.sql("SELECT id FROM instrument WHERE code = :code")
                .param("code", instrument.code())
                .query(Long.class)
                .single();
    }

    /** 같은 날 시세가 있으면 덮어쓴다(공공데이터 정정 반영). 하루 수백 건이라 건별 UPDATE→INSERT로 충분하다. */
    @Transactional
    public void upsertDailyPrices(long instrumentId, List<DailyPrice> prices) {
        for (DailyPrice p : prices) {
            int updated = jdbc.sql("""
                            UPDATE daily_price SET close = :close, adj_close = :adjClose, dividend = :dividend
                            WHERE instrument_id = :id AND date = :date
                            """)
                    .param("close", p.close())
                    .param("adjClose", p.adjClose())
                    .param("dividend", p.dividend())
                    .param("id", instrumentId)
                    .param("date", Date.valueOf(p.date()))
                    .update();
            if (updated == 0) {
                insertDailyPrices(instrumentId, List.of(p));
            }
        }
    }

    /** 비어 있는 구간에 한꺼번에 넣는다(시드·백필용). 이미 있는 날짜면 PK 위반으로 실패한다. */
    @Transactional
    public void insertDailyPrices(long instrumentId, List<DailyPrice> prices) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO daily_price (instrument_id, date, close, adj_close, dividend) VALUES (?, ?, ?, ?, ?)",
                prices,
                500,
                (ps, p) -> {
                    ps.setLong(1, instrumentId);
                    ps.setDate(2, Date.valueOf(p.date()));
                    ps.setBigDecimal(3, p.close());
                    ps.setBigDecimal(4, p.adjClose());
                    ps.setBigDecimal(5, p.dividend());
                });
    }

    private static ListedInstrument mapListed(ResultSet rs, int rowNum) throws SQLException {
        Instrument instrument = new Instrument(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("market"),
                CurrencyCode.valueOf(rs.getString("currency")),
                InstrumentType.valueOf(rs.getString("type")),
                rs.getBoolean("sample"));
        return new ListedInstrument(instrument, range(rs));
    }

    private static Optional<PriceRange> range(ResultSet rs) throws SQLException {
        Date first = rs.getDate("first_date");
        Date last = rs.getDate("last_date");
        return first == null || last == null
                ? Optional.empty()
                : Optional.of(new PriceRange(first.toLocalDate(), last.toLocalDate()));
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
