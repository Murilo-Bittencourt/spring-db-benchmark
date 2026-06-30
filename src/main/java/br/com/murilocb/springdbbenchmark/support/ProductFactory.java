package br.com.murilocb.springdbbenchmark.support;

import br.com.murilocb.springdbbenchmark.domain.Product;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProductFactory {

    private static final String[] CATEGORIES = {"ELECTRONICS", "CLOTHING", "FOOD", "HOME", "SPORTS"};

    public List<Product> build(int count, long skuOffset) {
        List<Product> list = new ArrayList<>(count);
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < count; i++) {
            long idx = skuOffset + i;
            list.add(Product.builder()
                    .sku("SKU-" + idx)
                    .name("Product " + idx)
                    .description("Description for product " + idx)
                    .price(BigDecimal.valueOf(10 + (idx % 990)))
                    .stock((int) (idx % 1000))
                    .category(CATEGORIES[(int) (idx % CATEGORIES.length)])
                    .active(true)
                    .createdAt(now)
                    .build());
        }
        return list;
    }
}
