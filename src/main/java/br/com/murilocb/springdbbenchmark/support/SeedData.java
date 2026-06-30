package br.com.murilocb.springdbbenchmark.support;

import br.com.murilocb.springdbbenchmark.domain.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory fixture dataset built once at application startup.
 * Replaces the former {@code products_staging} table — there is no need to
 * persist read-fixture data for a benchmark; holding it in memory avoids DB
 * round-trips and keeps the harness self-contained.
 *
 * Used to feed the SELECT operation's arrange phase (populates {@code products}
 * before measuring the read). Write operations (INSERT/UPDATE/DELETE/UPSERT)
 * keep generating their payloads per-run via {@link ProductFactory} because
 * INSERT WARM needs a distinct SKU offset to avoid UNIQUE collisions.
 */
@Slf4j
@Component
public class SeedData {

    private final ProductFactory factory;
    private final List<Product> seed;

    public SeedData(ProductFactory factory, @Value("${benchmark.seed.size:100000}") int size) {
        this.factory = factory;
        long t0 = System.currentTimeMillis();
        this.seed = factory.build(size, 0);
        log.info("In-memory seed built: {} products in {}ms", size, System.currentTimeMillis() - t0);
    }

    public int size() {
        return seed.size();
    }

    /**
     * Returns exactly {@code n} fixture products as FRESH copies (id == null).
     * Copies are mandatory: JPA strategies assign generated ids to the inserted
     * instances, so handing out the cached objects would pollute the seed and
     * break subsequent runs (stale id -> StaleObjectStateException after reset).
     */
    public List<Product> payload(int n) {
        if (n > seed.size()) return factory.build(n, 0);
        List<Product> copies = new ArrayList<>(n);
        for (Product p : seed.subList(0, n)) copies.add(copy(p));
        return copies;
    }

    private Product copy(Product p) {
        return Product.builder()
                .sku(p.getSku())
                .name(p.getName())
                .description(p.getDescription())
                .price(p.getPrice())
                .stock(p.getStock())
                .category(p.getCategory())
                .active(p.getActive())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
