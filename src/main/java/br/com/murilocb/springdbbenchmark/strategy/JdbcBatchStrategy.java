package br.com.murilocb.springdbbenchmark.strategy;

import br.com.murilocb.springdbbenchmark.domain.Product;
import br.com.murilocb.springdbbenchmark.support.ProductRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JdbcBatchStrategy implements BenchmarkStrategy {

    private final JdbcTemplate jdbc;

    private static final String INSERT_SQL =
            "INSERT INTO products (sku,name,description,price,stock,category,active,created_at) VALUES (?,?,?,?,?,?,?,?)";
    private static final String UPDATE_SQL =
            "UPDATE products SET price=?, stock=? WHERE id=?";
    private static final String DELETE_SQL =
            "DELETE FROM products WHERE id = ANY(?)";
    private static final String UPSERT_SQL =
            "INSERT INTO products (sku,name,description,price,stock,category,active,created_at) VALUES (?,?,?,?,?,?,?,?) " +
            "ON CONFLICT (sku) DO UPDATE SET name=EXCLUDED.name, price=EXCLUDED.price, stock=EXCLUDED.stock, active=EXCLUDED.active";

    @Override
    public String id() { return "S3_JDBC_BATCH"; }

    @Override
    public long insert(List<Product> batch) {
        int[] counts = jdbc.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                bindProduct(ps, batch.get(i));
            }
            @Override
            public int getBatchSize() { return batch.size(); }
        });
        return sumBatch(counts, batch.size());
    }

    @Override
    public long update(List<Product> existing) {
        int[] counts = jdbc.batchUpdate(UPDATE_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Product p = existing.get(i);
                ps.setBigDecimal(1, p.getPrice().add(BigDecimal.ONE));
                ps.setInt(2, p.getStock() + 1);
                ps.setLong(3, p.getId());
            }
            @Override
            public int getBatchSize() { return existing.size(); }
        });
        return sumBatch(counts, existing.size());
    }

    @Override
    public long delete(List<Long> ids) {
        return jdbc.update(con -> {
            var ps = con.prepareStatement(DELETE_SQL);
            ps.setArray(1, con.createArrayOf("bigint", ids.toArray()));
            return ps;
        });
    }

    @Override
    public long upsert(List<Product> batch) {
        int[] counts = jdbc.batchUpdate(UPSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                bindProduct(ps, batch.get(i));
            }
            @Override
            public int getBatchSize() { return batch.size(); }
        });
        return sumBatch(counts, batch.size());
    }

    @Override
    public long select(int limit) {
        return jdbc.query(ProductRowMapper.SELECT_SQL, ProductRowMapper.INSTANCE, limit).size();
    }

    /** Sums JDBC batch counts; falls back to expected size when the driver returns SUCCESS_NO_INFO (-2). */
    static long sumBatch(int[] counts, int expected) {
        long total = 0;
        for (int c : counts) {
            if (c < 0) return expected;
            total += c;
        }
        return total;
    }

    private void bindProduct(PreparedStatement ps, Product p) throws SQLException {
        ps.setString(1, p.getSku());
        ps.setString(2, p.getName());
        ps.setString(3, p.getDescription());
        ps.setBigDecimal(4, p.getPrice());
        ps.setInt(5, p.getStock());
        ps.setString(6, p.getCategory());
        ps.setBoolean(7, p.getActive());
        OffsetDateTime createdAt = p.getCreatedAt() != null ? p.getCreatedAt() : OffsetDateTime.now();
        ps.setTimestamp(8, Timestamp.from(createdAt.toInstant()));
    }
}
