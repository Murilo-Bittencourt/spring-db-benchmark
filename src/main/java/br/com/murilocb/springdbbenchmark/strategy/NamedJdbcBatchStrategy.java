package br.com.murilocb.springdbbenchmark.strategy;

import br.com.murilocb.springdbbenchmark.domain.Product;
import br.com.murilocb.springdbbenchmark.support.ProductRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class NamedJdbcBatchStrategy implements BenchmarkStrategy {

    private final NamedParameterJdbcTemplate namedJdbc;

    private static final String INSERT_SQL =
            "INSERT INTO products (sku,name,description,price,stock,category,active,created_at) " +
            "VALUES (:sku,:name,:description,:price,:stock,:category,:active,:createdAt)";
    private static final String UPDATE_SQL =
            "UPDATE products SET price=:price, stock=:stock WHERE id=:id";
    private static final String UPSERT_SQL =
            "INSERT INTO products (sku,name,description,price,stock,category,active,created_at) " +
            "VALUES (:sku,:name,:description,:price,:stock,:category,:active,:createdAt) " +
            "ON CONFLICT (sku) DO UPDATE SET name=EXCLUDED.name, price=EXCLUDED.price, stock=EXCLUDED.stock, active=EXCLUDED.active";

    @Override
    public String id() { return "S4_NAMED_JDBC_BATCH"; }

    @Override
    public long insert(List<Product> batch) {
        int[] counts = namedJdbc.batchUpdate(INSERT_SQL, toInsertParams(batch));
        return JdbcBatchStrategy.sumBatch(counts, batch.size());
    }

    @Override
    public long update(List<Product> existing) {
        SqlParameterSource[] params = existing.stream().map(p -> new MapSqlParameterSource()
                .addValue("price", p.getPrice().add(BigDecimal.ONE))
                .addValue("stock", p.getStock() + 1)
                .addValue("id", p.getId()))
                .toArray(SqlParameterSource[]::new);
        int[] counts = namedJdbc.batchUpdate(UPDATE_SQL, params);
        return JdbcBatchStrategy.sumBatch(counts, existing.size());
    }

    @Override
    public long delete(List<Long> ids) {
        return namedJdbc.update("DELETE FROM products WHERE id = ANY(:ids::bigint[])",
                new MapSqlParameterSource("ids", ids.toArray(Long[]::new)));
    }

    @Override
    public long upsert(List<Product> batch) {
        int[] counts = namedJdbc.batchUpdate(UPSERT_SQL, toInsertParams(batch));
        return JdbcBatchStrategy.sumBatch(counts, batch.size());
    }

    @Override
    public long select(int limit) {
        return namedJdbc.getJdbcTemplate()
                .query(ProductRowMapper.SELECT_SQL, ProductRowMapper.INSTANCE, limit).size();
    }

    private SqlParameterSource[] toInsertParams(List<Product> batch) {
        return batch.stream().map(p -> {
            OffsetDateTime createdAt = p.getCreatedAt() != null ? p.getCreatedAt() : OffsetDateTime.now();
            return new MapSqlParameterSource()
                    .addValue("sku", p.getSku())
                    .addValue("name", p.getName())
                    .addValue("description", p.getDescription())
                    .addValue("price", p.getPrice())
                    .addValue("stock", p.getStock())
                    .addValue("category", p.getCategory())
                    .addValue("active", p.getActive())
                    .addValue("createdAt", Timestamp.from(createdAt.toInstant()));
        }).toArray(SqlParameterSource[]::new);
    }
}
