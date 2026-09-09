package ar.com.padelnec.repository;

import ar.com.padelnec.domain.Product;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findAllByActiveTrueOrderByNameAsc();

    List<Product> findAllByOrderByNameAsc();
}
