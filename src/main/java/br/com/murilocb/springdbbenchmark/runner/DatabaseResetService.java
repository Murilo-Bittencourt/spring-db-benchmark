package br.com.murilocb.springdbbenchmark.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseResetService {

    private final JdbcTemplate jdbc;

    /**
     * Full isolation reset before each COLD run.
     * VACUUM FULL and CHECKPOINT must NOT run inside a transaction — JdbcTemplate
     * uses autocommit=true by default when no @Transactional is active, which is
     * the case here (this class has no @Transactional annotation).
     *
     * Requires superuser or pg_checkpoint role (PG15+) for CHECKPOINT and VACUUM FULL.
     */
    public void resetFor(String table) {
        log.debug("Resetting table: {}", table);
        jdbc.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY CASCADE");
        // VACUUM FULL after TRUNCATE: mostly useful for ANALYZE resetting planner stats.
        // Index rebuild benefit is minimal since table is already empty after TRUNCATE.
        jdbc.execute("VACUUM FULL ANALYZE " + table);
        jdbc.execute("CHECKPOINT");
        log.debug("Reset complete for: {}", table);
    }
}
