package br.com.murilocb.springdbbenchmark.strategy;

import br.com.murilocb.springdbbenchmark.domain.Product;

import java.util.List;

/**
 * Each operation returns the number of rows it affected/read, taken from the
 * JDBC/JPA return value (not pg_stat, which lags asynchronously).
 */
public interface BenchmarkStrategy {
    String id();
    long insert(List<Product> batch);
    long update(List<Product> existing);
    long delete(List<Long> ids);
    long upsert(List<Product> batch);
    /** Reads and materializes {@code limit} real rows; returns rows materialized. */
    long select(int limit);
}
