package br.com.murilocb.springdbbenchmark.metrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMetricsCollector {

    private final JdbcTemplate jdbc;

    private static final String WAL_SQL = "SELECT pg_wal_lsn_diff(pg_current_wal_lsn(), '0/0')";
    private static final String IO_SQL =
            "SELECT COALESCE(heap_blks_read,0) AS heap_blks_read, COALESCE(heap_blks_hit,0) AS heap_blks_hit " +
            "FROM pg_statio_user_tables WHERE relname='products'";
    private static final String TUPLES_SQL =
            "SELECT COALESCE(n_tup_ins,0) AS n_tup_ins, COALESCE(n_tup_upd,0) AS n_tup_upd, COALESCE(n_tup_del,0) AS n_tup_del " +
            "FROM pg_stat_user_tables WHERE relname='products'";
    // PG15/16: pg_stat_bgwriter has checkpoints_req + buffers_checkpoint
    // PG17+:    those moved to pg_stat_checkpointer as num_requested + buffers_written
    private static final String BGWRITER_SQL_PG15 =
            "SELECT checkpoints_req, buffers_checkpoint AS buffers_written FROM pg_stat_bgwriter";
    private static final String BGWRITER_SQL_PG17 =
            "SELECT num_requested AS checkpoints_req, buffers_written FROM pg_stat_checkpointer";

    private Boolean usePg17Checkpointer = null;

    public DatabaseSnapshot snapshot() {
        long walLsn = queryLong(WAL_SQL);

        Map<String, Object> io = queryRow(IO_SQL);
        long blksRead = toLong(io, "heap_blks_read");
        long blksHit = toLong(io, "heap_blks_hit");

        Map<String, Object> tup = queryRow(TUPLES_SQL);
        long tupIns = toLong(tup, "n_tup_ins");
        long tupUpd = toLong(tup, "n_tup_upd");
        long tupDel = toLong(tup, "n_tup_del");

        Map<String, Object> cp = queryCheckpointer();
        long checkpointsReq = toLong(cp, "checkpoints_req");
        long buffersWritten = toLong(cp, "buffers_written");

        return new DatabaseSnapshot(walLsn, blksRead, blksHit, tupIns, tupUpd, tupDel, checkpointsReq, buffersWritten);
    }

    /**
     * Fix-A for cross-run stat contamination: zero the table's cumulative counters
     * and drop the session's stats snapshot so before/after deltas reflect only the
     * measured operation. Does not eliminate the ~1s flush lag (see EXPLAIN BUFFERS
     * path for accurate per-query read I/O).
     */
    public void resetTableStats(String table) {
        execQuiet("SELECT pg_stat_reset_single_table_counters('" + table + "'::regclass)");
        clearSnapshot();
    }

    public void clearSnapshot() {
        execQuiet("SELECT pg_stat_clear_snapshot()");
    }

    private static final Pattern BUFFERS_PATTERN =
            Pattern.compile("shared hit=(\\d+)(?: read=(\\d+))?");

    /**
     * Exact per-query buffer accounting via EXPLAIN (ANALYZE, BUFFERS). Unlike
     * pg_statio_* this has no async lag. ANALYZE actually executes the query.
     */
    public BufferStats explainBuffers(int limit) {
        String sql = "EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM products LIMIT " + limit;
        long hit = 0, read = 0;
        try {
            List<String> lines = jdbc.queryForList(sql, String.class);
            for (String line : lines) {
                Matcher m = BUFFERS_PATTERN.matcher(line);
                if (m.find()) {
                    hit += Long.parseLong(m.group(1));
                    if (m.group(2) != null) read += Long.parseLong(m.group(2));
                }
            }
        } catch (Exception e) {
            log.warn("EXPLAIN BUFFERS failed: {}", e.getMessage());
        }
        return new BufferStats(hit, read);
    }

    public record BufferStats(long hit, long read) {}

    private void execQuiet(String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception e) {
            log.warn("Metric stmt failed [{}]: {}", sql, e.getMessage());
        }
    }

    private long queryLong(String sql) {
        try {
            Long val = jdbc.queryForObject(sql, Long.class);
            return val == null ? 0 : val;
        } catch (Exception e) {
            log.warn("Metric query failed [{}]: {}", sql, e.getMessage());
            return 0;
        }
    }

    private Map<String, Object> queryRow(String sql) {
        try {
            var rows = jdbc.queryForList(sql);
            return rows.isEmpty() ? Map.of() : rows.get(0);
        } catch (Exception e) {
            log.warn("Metric query failed [{}]: {}", sql, e.getMessage());
            return Map.of();
        }
    }

    private Map<String, Object> queryCheckpointer() {
        if (usePg17Checkpointer == null) {
            try {
                jdbc.queryForList(BGWRITER_SQL_PG17);
                usePg17Checkpointer = true;
            } catch (Exception e) {
                usePg17Checkpointer = false;
            }
        }
        return queryRow(usePg17Checkpointer ? BGWRITER_SQL_PG17 : BGWRITER_SQL_PG15);
    }

    private long toLong(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val instanceof Number n) return n.longValue();
        return 0L;
    }
}
