package br.com.murilocb.springdbbenchmark.support;

import br.com.murilocb.springdbbenchmark.domain.Product;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

/**
 * Maps a full products row to a Product so SELECT benchmarks actually transfer
 * and materialize the columns (apples-to-apples with the JPA strategies, which
 * hydrate entities). Shared by the JDBC-based strategies.
 */
public final class ProductRowMapper implements RowMapper<Product> {

    public static final ProductRowMapper INSTANCE = new ProductRowMapper();

    public static final String SELECT_SQL =
            "SELECT id, sku, name, description, price, stock, category, active, created_at FROM products LIMIT ?";

    @Override
    public Product mapRow(ResultSet rs, int rowNum) throws SQLException {
        return Product.builder()
                .id(rs.getLong("id"))
                .sku(rs.getString("sku"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .price(rs.getBigDecimal("price"))
                .stock(rs.getInt("stock"))
                .category(rs.getString("category"))
                .active(rs.getBoolean("active"))
                .createdAt(rs.getObject("created_at", OffsetDateTime.class))
                .build();
    }
}
