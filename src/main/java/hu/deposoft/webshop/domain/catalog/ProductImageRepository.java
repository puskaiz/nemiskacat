package hu.deposoft.webshop.domain.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    Optional<ProductImage> findByProductAndStorageKey(Product product, String storageKey);

    java.util.List<ProductImage> findByProductOrderByPositionAsc(Product product);

    Optional<ProductImage> findFirstByProductOrderByPositionAsc(Product product);

    boolean existsByStorageKey(String storageKey);
}
