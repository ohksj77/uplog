package com.uplog.marketdata.feed;

import com.uplog.core.CurrencyCode;
import com.uplog.marketdata.Instrument;
import com.uplog.marketdata.InstrumentType;
import com.uplog.marketdata.ListedInstrument;
import com.uplog.marketdata.NewInstrument;
import com.uplog.marketdata.PriceRange;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 종목 유니버스·일봉 확인 구간(price_coverage)·종목 검색. SQL은 PostgreSQL과 H2(PostgreSQL 모드) 공통 문법만 쓴다. */
@Component
public class FeedRepository {

    /** 일봉 확인 구간. */
    public record Coverage(LocalDate from, LocalDate to) {}

    private record Existing(long id, String name, String market, String currency, String type, boolean sample) {}

    private final JdbcTemplate jdbcTemplate;
    private final JdbcClient jdbc;

    public FeedRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    // ---- 유니버스

    public Optional<LocalDate> universeSyncedOn(MarketFeed.ListingMarket market) {
        return jdbc.sql("SELECT synced_on FROM instrument_universe_sync WHERE listing_market = :m")
                .param("m", market.name())
                .query((rs, n) -> rs.getDate("synced_on").toLocalDate())
                .optional();
    }

    /**
     * 실제 종목을 한꺼번에 넣거나 고친다(이름·시장·통화·유형). 같은 코드의 샘플 종목은 건드리지 않는다.
     *
     * @return 새로 넣거나 고친 종목 수
     */
    @Transactional
    public int upsertRealInstruments(List<NewInstrument> instruments, MarketFeed.ListingMarket syncedMarket, LocalDate syncedOn) {
        Map<String, Existing> existing = new HashMap<>();
        jdbc.sql("SELECT id, code, name, market, currency, type, sample FROM instrument").query(rs -> {
            existing.put(rs.getString("code"), new Existing(rs.getLong("id"), rs.getString("name"), rs.getString("market"),
                    rs.getString("currency"), rs.getString("type"), rs.getBoolean("sample")));
        });
        List<NewInstrument> inserts = new ArrayList<>();
        List<Object[]> updates = new ArrayList<>();
        for (NewInstrument i : instruments) {
            Existing e = existing.get(i.code());
            if (e == null) {
                inserts.add(i);
            } else if (!e.sample() && (!e.name().equals(i.name()) || !e.market().equals(i.market())
                    || !e.currency().equals(i.currency().name()) || !e.type().equals(i.type().name()))) {
                updates.add(new Object[] {i.name(), i.market(), i.currency().name(), i.type().name(), e.id()});
            }
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO instrument (code, name, market, currency, type, sample) VALUES (?, ?, ?, ?, ?, FALSE)",
                inserts,
                500,
                (ps, i) -> {
                    ps.setString(1, i.code());
                    ps.setString(2, i.name());
                    ps.setString(3, i.market());
                    ps.setString(4, i.currency().name());
                    ps.setString(5, i.type().name());
                });
        jdbcTemplate.batchUpdate("UPDATE instrument SET name = ?, market = ?, currency = ?, type = ? WHERE id = ?", updates);
        if (syncedMarket != null) {
            int updated = jdbc.sql("""
                            UPDATE instrument_universe_sync SET synced_on = :d, instrument_count = :n WHERE listing_market = :m
                            """)
                    .param("d", Date.valueOf(syncedOn))
                    .param("n", instruments.size())
                    .param("m", syncedMarket.name())
                    .update();
            if (updated == 0) {
                jdbc.sql("""
                                INSERT INTO instrument_universe_sync (listing_market, synced_on, instrument_count)
                                VALUES (:m, :d, :n)
                                """)
                        .param("d", Date.valueOf(syncedOn))
                        .param("n", instruments.size())
                        .param("m", syncedMarket.name())
                        .update();
            }
        }
        return inserts.size() + updates.size();
    }

    // ---- 일봉 확인 구간

    /** 확인 구간. 기록이 없으면 이미 쌓인 시세 구간(수집 작업이 넣은 것)을 확인 구간으로 본다. */
    public Optional<Coverage> coverage(long instrumentId) {
        Optional<Coverage> recorded = jdbc.sql("SELECT covered_from, covered_to FROM price_coverage WHERE instrument_id = :id")
                .param("id", instrumentId)
                .query((rs, n) -> new Coverage(rs.getDate("covered_from").toLocalDate(), rs.getDate("covered_to").toLocalDate()))
                .optional();
        if (recorded.isPresent()) {
            return recorded;
        }
        return jdbc.sql("SELECT MIN(date) AS first_date, MAX(date) AS last_date FROM daily_price WHERE instrument_id = :id")
                .param("id", instrumentId)
                .query((rs, n) -> rs.getDate("first_date") == null ? null
                        : new Coverage(rs.getDate("first_date").toLocalDate(), rs.getDate("last_date").toLocalDate()))
                .optional();
    }

    @Transactional
    public void saveCoverage(long instrumentId, Coverage coverage, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE price_coverage SET covered_from = :f, covered_to = :t, updated_at = :u WHERE instrument_id = :id
                        """)
                .param("f", Date.valueOf(coverage.from()))
                .param("t", Date.valueOf(coverage.to()))
                .param("u", Timestamp.from(now))
                .param("id", instrumentId)
                .update();
        if (updated == 0) {
            jdbc.sql("""
                            INSERT INTO price_coverage (instrument_id, covered_from, covered_to, updated_at)
                            VALUES (:id, :f, :t, :u)
                            """)
                    .param("f", Date.valueOf(coverage.from()))
                    .param("t", Date.valueOf(coverage.to()))
                    .param("u", Timestamp.from(now))
                    .param("id", instrumentId)
                    .update();
        }
    }

    // ---- 검색

    /**
     * 실제 ETF·주식. 코드가 정확히 같은 종목 → ETF → 주식 순이고, 같은 무리 안에서는 코드·이름이 검색어로 시작하는 것 → 이름순.
     * 시세가 아직 없는 종목도 나온다(시뮬레이션할 때 받는다).
     */
    public List<ListedInstrument> searchRealInstruments(String query, int limit) {
        String q = normalize(query);
        List<Instrument> found = jdbc.sql("""
                        SELECT i.id, i.code, i.name, i.market, i.currency, i.type, i.sample FROM instrument i
                        WHERE i.sample = FALSE AND i.type IN ('ETF', 'STOCK')
                          AND (LOWER(i.name) LIKE :pattern ESCAPE '\\' OR LOWER(i.code) LIKE :pattern ESCAPE '\\')
                        ORDER BY CASE WHEN LOWER(i.code) = :exact THEN 0 ELSE 1 END,
                                 CASE WHEN i.type = 'ETF' THEN 0 ELSE 1 END,
                                 CASE WHEN LOWER(i.code) LIKE :prefix ESCAPE '\\' OR LOWER(i.name) LIKE :prefix ESCAPE '\\'
                                      THEN 0 ELSE 1 END,
                                 i.name, i.code
                        LIMIT :limit
                        """)
                .param("pattern", "%" + escapeLike(q) + "%")
                .param("prefix", escapeLike(q) + "%")
                .param("exact", q)
                .param("limit", limit)
                .query(FeedRepository::instrument)
                .list();
        return withRanges(found);
    }

    private List<ListedInstrument> withRanges(List<Instrument> instruments) {
        if (instruments.isEmpty()) {
            return List.of();
        }
        Map<Long, PriceRange> ranges = new HashMap<>();
        jdbc.sql("""
                        SELECT instrument_id, MIN(date) AS first_date, MAX(date) AS last_date FROM daily_price
                        WHERE instrument_id IN (:ids) GROUP BY instrument_id
                        """)
                .param("ids", instruments.stream().map(Instrument::id).toList())
                .query(rs -> {
                    ranges.put(rs.getLong("instrument_id"),
                            new PriceRange(rs.getDate("first_date").toLocalDate(), rs.getDate("last_date").toLocalDate()));
                });
        return instruments.stream().map(i -> new ListedInstrument(i, Optional.ofNullable(ranges.get(i.id())))).toList();
    }

    private static Instrument instrument(ResultSet rs, int rowNum) throws SQLException {
        return new Instrument(
                rs.getLong("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("market"),
                CurrencyCode.valueOf(rs.getString("currency")),
                InstrumentType.valueOf(rs.getString("type")),
                rs.getBoolean("sample"));
    }

    private static String normalize(String query) {
        return query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
