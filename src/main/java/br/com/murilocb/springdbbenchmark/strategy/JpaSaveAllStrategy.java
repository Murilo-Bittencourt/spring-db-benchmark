package br.com.murilocb.springdbbenchmark.strategy;

import br.com.murilocb.springdbbenchmark.domain.Product;
import br.com.murilocb.springdbbenchmark.repository.ProductJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JpaSaveAllStrategy implements BenchmarkStrategy {

    private final ProductJpaRepository repo;
    private final JdbcTemplate jdbc;

    @Override
    public String id() { return "S1_JPA_SAVE_ALL"; }

    @Override
    @Transactional
    public long insert(List<Product> batch) {
        return repo.saveAll(batch).size();
    }

    @Override
    @Transactional
    public long update(List<Product> existing) {
        existing.forEach(p -> {
            p.setPrice(p.getPrice().add(BigDecimal.ONE));
            p.setStock(p.getStock() + 1);
        });
        return repo.saveAll(existing).size();
    }

    @Override
    @Transactional
    public long delete(List<Long> ids) {
        repo.deleteAllByIdInBatch(ids);
        return ids.size();
    }

    @Override
    @Transactional
    public long upsert(List<Product> batch) {
        // JPA has no native upsert by business key. Resolve existing sku->id so
        // merge/save updates matching rows instead of inserting (UNIQUE violation).
        Map<String, Long> existing = loadSkuIds();
        for (Product p : batch) {
            Long id = existing.get(p.getSku());
            if (id != null) p.setId(id);
        }
        return repo.saveAll(batch).size();
    }

    @Override
    @Transactional(readOnly = true)
    public long select(int limit) {
        // Materializes real entities (limit rows), not a count query.
        return repo.findAll(PageRequest.of(0, limit)).getContent().size();
    }

    private Map<String, Long> loadSkuIds() {
        Map<String, Long> map = new HashMap<>();
        jdbc.query("SELECT sku, id FROM products",
                rs -> { map.put(rs.getString(1), rs.getLong(2)); });
        return map;
    }
}
