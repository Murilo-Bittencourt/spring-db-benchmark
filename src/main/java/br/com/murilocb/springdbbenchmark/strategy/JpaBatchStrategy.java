package br.com.murilocb.springdbbenchmark.strategy;

import br.com.murilocb.springdbbenchmark.domain.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JpaBatchStrategy implements BenchmarkStrategy {

    @PersistenceContext
    private EntityManager em;

    private final JdbcTemplate jdbc;

    @Value("${spring.jpa.properties.hibernate.jdbc.batch_size:500}")
    private int batchSize;

    @Override
    public String id() { return "S2_JPA_BATCH"; }

    @Override
    @Transactional
    public long insert(List<Product> batch) {
        for (int i = 0; i < batch.size(); i++) {
            em.persist(batch.get(i));
            if ((i + 1) % batchSize == 0) {
                em.flush();
                em.clear();
            }
        }
        return batch.size();
    }

    @Override
    @Transactional
    public long update(List<Product> existing) {
        for (int i = 0; i < existing.size(); i++) {
            Product p = existing.get(i);
            p.setPrice(p.getPrice().add(BigDecimal.ONE));
            p.setStock(p.getStock() + 1);
            em.merge(p);
            if ((i + 1) % batchSize == 0) {
                em.flush();
                em.clear();
            }
        }
        return existing.size();
    }

    @Override
    @Transactional
    public long delete(List<Long> ids) {
        Long[] idArray = ids.toArray(Long[]::new);
        return jdbc.update("DELETE FROM products WHERE id = ANY(?::bigint[])", (Object) idArray);
    }

    @Override
    @Transactional
    public long upsert(List<Product> batch) {
        // JPA has no native upsert by business key. Resolve existing sku->id so
        // merge updates matching rows instead of inserting (UNIQUE violation).
        Map<String, Long> existing = loadSkuIds();
        for (int i = 0; i < batch.size(); i++) {
            Product p = batch.get(i);
            Long id = existing.get(p.getSku());
            if (id != null) p.setId(id);
            em.merge(p);
            if ((i + 1) % batchSize == 0) {
                em.flush();
                em.clear();
            }
        }
        return batch.size();
    }

    private Map<String, Long> loadSkuIds() {
        Map<String, Long> map = new HashMap<>();
        jdbc.query("SELECT sku, id FROM products",
                rs -> { map.put(rs.getString(1), rs.getLong(2)); });
        return map;
    }

    @Override
    @Transactional(readOnly = true)
    public long select(int limit) {
        // Materializes real entities (limit rows).
        return em.createQuery("SELECT p FROM Product p", Product.class)
                .setMaxResults(limit)
                .getResultList()
                .size();
    }
}
