package com.uplog.ingest;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * ingest_job 큐. 작업은 {@code SELECT … FOR UPDATE SKIP LOCKED}로 집어 같은 작업을 두 워커가 돌리지 않게 한다.
 * 집는 트랜잭션은 짧게(상태를 RUNNING으로 바꾸고 바로 커밋) 두고, 작업 자체는 트랜잭션 밖에서 돈다.
 */
public class IngestJobRepository {

    private static final String COLUMNS = """
            id, account_id, kind, as_of, state, attempts, next_run_at, last_error, error_reason, progress, created_at, finished_at
            """;

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;

    public IngestJobRepository(JdbcClient jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    /** 같은 계좌·종류·기준일의 작업이 대기·실행 중이면 그 id를 돌려준다. */
    public long enqueue(long accountId, IngestJob.Kind kind, LocalDate asOf, Instant now) {
        Long id = tx.execute(status -> {
            var select = jdbc.sql("""
                            SELECT id FROM ingest_job
                            WHERE account_id = :accountId AND kind = :kind AND state IN ('PENDING', 'RUNNING')
                            """ + (asOf == null ? " AND as_of IS NULL" : " AND as_of = :asOf") + " ORDER BY id LIMIT 1")
                    .param("accountId", accountId)
                    .param("kind", kind.name());
            if (asOf != null) {
                select = select.param("asOf", Date.valueOf(asOf));
            }
            Optional<Long> existing = select.query(Long.class).optional();
            if (existing.isPresent()) {
                return existing.get();
            }
            jdbc.sql("""
                            INSERT INTO ingest_job (account_id, kind, as_of, state, attempts, next_run_at, progress, created_at)
                            VALUES (:accountId, :kind, :asOf, 'PENDING', 0, :now, 0, :now)
                            """)
                    .param("accountId", accountId)
                    .param("kind", kind.name())
                    .param("asOf", asOf == null ? null : Date.valueOf(asOf), Types.DATE)
                    .param("now", Timestamp.from(now))
                    .update();
            return jdbc.sql("SELECT MAX(id) FROM ingest_job WHERE account_id = :accountId AND kind = :kind")
                    .param("accountId", accountId)
                    .param("kind", kind.name())
                    .query(Long.class)
                    .single();
        });
        return id;
    }

    /** 돌릴 때가 된 작업 하나를 집어 RUNNING으로 바꾼다. 오래 멈춘 RUNNING 작업은 먼저 대기로 되돌린다. */
    public Optional<IngestJob> claim(String worker, Instant now, Duration lockTimeout) {
        return tx.execute(status -> {
            jdbc.sql("""
                            UPDATE ingest_job SET state = 'PENDING', locked_by = NULL, locked_at = NULL
                            WHERE state = 'RUNNING' AND locked_at < :staleBefore
                            """)
                    .param("staleBefore", Timestamp.from(now.minus(lockTimeout)))
                    .update();
            Optional<Long> id = jdbc.sql("""
                            SELECT id FROM ingest_job
                            WHERE state = 'PENDING' AND next_run_at <= :now
                            ORDER BY next_run_at, id
                            LIMIT 1
                            FOR UPDATE SKIP LOCKED
                            """)
                    .param("now", Timestamp.from(now))
                    .query(Long.class)
                    .optional();
            if (id.isEmpty()) {
                return Optional.<IngestJob>empty();
            }
            jdbc.sql("""
                            UPDATE ingest_job SET state = 'RUNNING', locked_by = :worker, locked_at = :now, attempts = attempts + 1
                            WHERE id = :id
                            """)
                    .param("worker", worker)
                    .param("now", Timestamp.from(now))
                    .param("id", id.get())
                    .update();
            return find(id.get());
        });
    }

    public Optional<IngestJob> find(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM ingest_job WHERE id = :id")
                .param("id", id)
                .query(IngestJobRepository::map)
                .optional();
    }

    public void progress(long id, double progress) {
        jdbc.sql("UPDATE ingest_job SET progress = :progress WHERE id = :id")
                .param("progress", Math.max(0.0, Math.min(1.0, progress)))
                .param("id", id)
                .update();
    }

    public void complete(long id, String note, Instant now) {
        jdbc.sql("""
                        UPDATE ingest_job SET state = 'DONE', progress = 1, note = :note, last_error = NULL, error_reason = NULL,
                               locked_by = NULL, locked_at = NULL, finished_at = :now
                        WHERE id = :id
                        """)
                .param("note", truncate(note, 1000))
                .param("now", Timestamp.from(now))
                .param("id", id)
                .update();
    }

    /** 실패했지만 다시 시도한다. */
    public void retry(long id, Instant nextRunAt, String reason, String message) {
        jdbc.sql("""
                        UPDATE ingest_job SET state = 'PENDING', next_run_at = :next, error_reason = :reason, last_error = :message,
                               locked_by = NULL, locked_at = NULL
                        WHERE id = :id
                        """)
                .param("next", Timestamp.from(nextRunAt))
                .param("reason", truncate(reason, 40))
                .param("message", truncate(message, 500))
                .param("id", id)
                .update();
    }

    public void fail(long id, String reason, String message, Instant now) {
        jdbc.sql("""
                        UPDATE ingest_job SET state = 'FAILED', error_reason = :reason, last_error = :message,
                               locked_by = NULL, locked_at = NULL, finished_at = :now
                        WHERE id = :id
                        """)
                .param("reason", truncate(reason, 40))
                .param("message", truncate(message, 500))
                .param("now", Timestamp.from(now))
                .param("id", id)
                .update();
    }

    /** 계좌의 가장 최근 작업(종류 null이면 전체). */
    public Optional<IngestJob> latest(long accountId, IngestJob.Kind kind) {
        var select = jdbc.sql("SELECT " + COLUMNS + " FROM ingest_job WHERE account_id = :accountId"
                        + (kind == null ? "" : " AND kind = :kind") + " ORDER BY id DESC LIMIT 1")
                .param("accountId", accountId);
        if (kind != null) {
            select = select.param("kind", kind.name());
        }
        return select.query(IngestJobRepository::map).optional();
    }

    /** 끝났거나(성공·실패) 실패를 한 번이라도 겪은 가장 최근 작업. */
    public Optional<IngestJob> latestSettled(long accountId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                         FROM ingest_job
                        WHERE account_id = :accountId AND (state IN ('DONE', 'FAILED') OR last_error IS NOT NULL)
                        ORDER BY id DESC LIMIT 1
                        """)
                .param("accountId", accountId)
                .query(IngestJobRepository::map)
                .optional();
    }

    /** 마지막으로 성공한 일일 수집 시각. */
    public Optional<Instant> lastDailyDone(long accountId) {
        return jdbc.sql("""
                        SELECT MAX(finished_at) FROM ingest_job WHERE account_id = :accountId AND kind = 'DAILY' AND state = 'DONE'
                        """)
                .param("accountId", accountId)
                .query((rs, n) -> Optional.ofNullable(rs.getTimestamp(1)).map(Timestamp::toInstant))
                .single();
    }

    private static IngestJob map(ResultSet rs, int rowNum) throws SQLException {
        Date asOf = rs.getDate("as_of");
        Timestamp finished = rs.getTimestamp("finished_at");
        return new IngestJob(
                rs.getLong("id"),
                rs.getLong("account_id"),
                IngestJob.Kind.valueOf(rs.getString("kind")),
                asOf == null ? null : asOf.toLocalDate(),
                IngestJob.State.valueOf(rs.getString("state")),
                rs.getInt("attempts"),
                rs.getTimestamp("next_run_at").toInstant(),
                rs.getString("last_error"),
                rs.getString("error_reason"),
                rs.getDouble("progress"),
                rs.getTimestamp("created_at").toInstant(),
                finished == null ? null : finished.toInstant());
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
