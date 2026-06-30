package br.com.murilocb.springdbbenchmark.runner;

import br.com.murilocb.springdbbenchmark.domain.Product;
import br.com.murilocb.springdbbenchmark.metrics.BenchmarkResult;
import br.com.murilocb.springdbbenchmark.metrics.DatabaseMetricsCollector;
import br.com.murilocb.springdbbenchmark.metrics.DatabaseMetricsDelta;
import br.com.murilocb.springdbbenchmark.metrics.DatabaseSnapshot;
import br.com.murilocb.springdbbenchmark.model.Operation;
import br.com.murilocb.springdbbenchmark.model.RunType;
import br.com.murilocb.springdbbenchmark.strategy.BenchmarkStrategy;
import br.com.murilocb.springdbbenchmark.support.ProductFactory;
import br.com.murilocb.springdbbenchmark.support.SeedData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BenchmarkRunner {

    private final DatabaseResetService resetService;
    private final DatabaseMetricsCollector collector;
    private final ProductFactory factory;
    private final SeedData seedData;
    private final JdbcTemplate jdbc;

    public List<BenchmarkResult> run(BenchmarkStrategy strategy, Operation operation, int volume) {
        List<BenchmarkResult> results = new ArrayList<>(2);
        results.add(runOnce(strategy, operation, volume, RunType.COLD));
        results.add(runOnce(strategy, operation, volume, RunType.WARM));
        return results;
    }

    private BenchmarkResult runOnce(BenchmarkStrategy strategy, Operation operation, int volume, RunType runType) {
        boolean cold = runType == RunType.COLD;
        log.info("[{}] {} {} ({})", strategy.id(), operation, volume, runType);

        arrange(strategy, operation, volume, cold);

        // Fix-A: clean per-op deltas (kills cross-run stat contamination).
        collector.resetTableStats("products");

        // Measure only the act phase
        Runtime rt = Runtime.getRuntime();
        System.gc();
        long memBefore = rt.totalMemory() - rt.freeMemory();
        DatabaseSnapshot before = collector.snapshot();
        long t0 = System.nanoTime();

        long rowsAffected = act(strategy, operation, volume, cold);

        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        collector.clearSnapshot();
        DatabaseSnapshot after = collector.snapshot();
        System.gc();
        long memAfter = rt.totalMemory() - rt.freeMemory();

        long memDeltaKb = (memAfter - memBefore) / 1024;
        DatabaseMetricsDelta delta = DatabaseMetricsDelta.of(before, after);

        // Read I/O: SELECT uses exact EXPLAIN BUFFERS (no lag); writes use the
        // pg_statio delta (indicative — flush lag may under-report short ops).
        long blocksHit = delta.heapBlocksHit();
        long blocksRead = delta.heapBlocksRead();
        if (operation == Operation.SELECT) {
            var buf = collector.explainBuffers(volume);
            blocksHit = buf.hit();
            blocksRead = buf.read();
        }

        return BenchmarkResult.from(strategy.id(), operation, volume, runType,
                elapsedMs, memDeltaKb, rowsAffected, blocksHit, blocksRead, delta);
    }

    /**
     * Arrange: prepare state required for the operation, NOT measured.
     * Each operation defines what state must pre-exist for both COLD and WARM runs.
     */
    private void arrange(BenchmarkStrategy strategy, Operation operation, int volume, boolean cold) {
        switch (operation) {
            case INSERT -> {
                // COLD: reset so table is empty (INSERT goes into clean table)
                // WARM: no reset — WARM INSERT uses skuOffset=volume to avoid UNIQUE collision
                if (cold) resetService.resetFor("products");
            }
            case UPDATE, DELETE -> {
                // Both COLD and WARM: reset + pre-insert N rows (measured = updating/deleting those rows)
                resetService.resetFor("products");
                strategy.insert(factory.build(volume, 0));
            }
            case UPSERT -> {
                // COLD: reset + pre-insert half (measured = upsert N: half insert, half update)
                // WARM: no extra arrange (table already has N/2; measured = upsert N again, all update)
                if (cold) {
                    resetService.resetFor("products");
                    strategy.insert(factory.build(volume / 2, 0));
                }
            }
            case SELECT -> {
                // COLD: reset + load fixture from in-memory seed into products (not measured)
                // WARM: no reset — data stays, measures warm cache read
                if (cold) {
                    resetService.resetFor("products");
                    strategy.insert(seedData.payload(volume));
                }
            }
        }
    }

    /**
     * Act: the measured operation.
     */
    private long act(BenchmarkStrategy strategy, Operation operation, int volume, boolean cold) {
        return switch (operation) {
            case INSERT -> {
                // COLD uses skuOffset=0, WARM uses skuOffset=volume to avoid UNIQUE collision
                long offset = cold ? 0 : volume;
                yield strategy.insert(factory.build(volume, offset));
            }
            case UPDATE -> {
                List<Long> ids = loadInsertedIds(volume);
                List<Product> existing = idsToProducts(ids, factory.build(volume, 0));
                yield strategy.update(existing);
            }
            case DELETE -> {
                List<Long> ids = loadInsertedIds(volume);
                yield strategy.delete(ids);
            }
            case UPSERT ->
                // COLD: upsert N (first half=update existing N/2, second half=insert new N/2)
                // WARM: upsert N (all update since all N were inserted in COLD arrange or prior WARM)
                strategy.upsert(factory.build(volume, 0));
            case SELECT -> strategy.select(volume);
        };
    }

    private List<Long> loadInsertedIds(int limit) {
        return jdbc.queryForList(
                "SELECT id FROM products ORDER BY id LIMIT ?", Long.class, limit);
    }

    private List<Product> idsToProducts(List<Long> ids, List<Product> template) {
        List<Product> result = new ArrayList<>(ids.size());
        for (int i = 0; i < ids.size() && i < template.size(); i++) {
            template.get(i).setId(ids.get(i));
            result.add(template.get(i));
        }
        return result;
    }
}
