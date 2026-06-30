package br.com.murilocb.springdbbenchmark.repository;

import br.com.murilocb.springdbbenchmark.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
}
