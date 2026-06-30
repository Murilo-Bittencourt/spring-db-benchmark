package br.com.murilocb.springdbbenchmark.strategy;

import br.com.murilocb.springdbbenchmark.domain.Product;
import br.com.murilocb.springdbbenchmark.support.ProductRowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CopyManagerStrategy implements BenchmarkStrategy {

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX");

    @Override
    public String id() { return "S5_COPY_MANAGER"; }

    @Override
    public long insert(List<Product> batch) {
        return copyToTable("products", batch);
    }

    private long copyToTable(String table, List<Product> batch) {
        String sql = "COPY " + table +
                " (sku,name,description,price,stock,category,active,created_at) FROM STDIN WITH (FORMAT CSV, NULL '')";
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
            PGConnection pgConn = conn.unwrap(PGConnection.class);

            PipedOutputStream pos = new PipedOutputStream();
            PipedInputStream pis = new PipedInputStream(pos, 1 << 16);

            Thread writer = Thread.ofVirtual().start(() -> {
                try (PrintWriter pw = new PrintWriter(pos)) {
                    for (Product p : batch) {
                        OffsetDateTime createdAt = p.getCreatedAt() != null ? p.getCreatedAt() : OffsetDateTime.now();
                        pw.printf("%s,%s,%s,%s,%d,%s,%b,%s%n",
                                escapeCsv(p.getSku()),
                                escapeCsv(p.getName()),
                                escapeCsv(p.getDescription()),
                                p.getPrice().toPlainString(),
                                p.getStock(),
                                escapeCsv(p.getCategory()),
                                p.getActive(),
                                createdAt.format(TS_FMT));
                    }
                } catch (Exception e) {
                    log.error("CopyManager writer error", e);
                }
            });

            long rows = pgConn.getCopyAPI().copyIn(sql, pis);
            writer.join();
            return rows;
        } catch (SQLException | IOException | InterruptedException e) {
            throw new RuntimeException("CopyManager failed", e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    @Override
    public long update(List<Product> existing) {
        int[][] counts = jdbc.batchUpdate("UPDATE products SET price=?, stock=? WHERE id=?",
                existing, existing.size(),
                (ps, p) -> {
                    try {
                        ps.setBigDecimal(1, p.getPrice().add(BigDecimal.ONE));
                        ps.setInt(2, p.getStock() + 1);
                        ps.setLong(3, p.getId());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        return sumChunks(counts, existing.size());
    }

    @Override
    public long delete(List<Long> ids) {
        return jdbc.update(con -> {
            var ps = con.prepareStatement("DELETE FROM products WHERE id = ANY(?)");
            ps.setArray(1, con.createArrayOf("bigint", ids.toArray()));
            return ps;
        });
    }

    @Override
    public long upsert(List<Product> batch) {
        // COPY doesn't support ON CONFLICT; fall back to jdbc batch upsert
        int[][] counts = jdbc.batchUpdate(
                "INSERT INTO products (sku,name,description,price,stock,category,active,created_at) VALUES (?,?,?,?,?,?,?,?) " +
                "ON CONFLICT (sku) DO UPDATE SET name=EXCLUDED.name, price=EXCLUDED.price, stock=EXCLUDED.stock, active=EXCLUDED.active",
                batch, batch.size(),
                (ps, p) -> {
                    try {
                        OffsetDateTime createdAt = p.getCreatedAt() != null ? p.getCreatedAt() : OffsetDateTime.now();
                        ps.setString(1, p.getSku());
                        ps.setString(2, p.getName());
                        ps.setString(3, p.getDescription());
                        ps.setBigDecimal(4, p.getPrice());
                        ps.setInt(5, p.getStock());
                        ps.setString(6, p.getCategory());
                        ps.setBoolean(7, p.getActive());
                        ps.setTimestamp(8, java.sql.Timestamp.from(createdAt.toInstant()));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        return sumChunks(counts, batch.size());
    }

    @Override
    public long select(int limit) {
        return jdbc.query(ProductRowMapper.SELECT_SQL, ProductRowMapper.INSTANCE, limit).size();
    }

    /** Sums the per-chunk batch counts from JdbcTemplate.batchUpdate(...batchSize...). */
    private long sumChunks(int[][] chunks, int expected) {
        long total = 0;
        for (int[] chunk : chunks) {
            for (int c : chunk) {
                if (c < 0) return expected;
                total += c;
            }
        }
        return total;
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
