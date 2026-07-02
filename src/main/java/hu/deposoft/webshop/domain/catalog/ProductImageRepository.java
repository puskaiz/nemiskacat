package hu.deposoft.webshop.domain.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    Optional<ProductImage> findByProductAndStorageKey(Product product, String storageKey);

    java.util.List<ProductImage> findByProductOrderByPositionAsc(Product product);

    Optional<ProductImage> findFirstByProductOrderByPositionAsc(Product product);

    boolean existsByStorageKey(String storageKey);

    /** All images whose key is still a legacy hot-link key (e.g. {@code wp/…}) — used by
     *  the one-time image backfill to relocate them into local storage. */
    java.util.List<ProductImage> findByStorageKeyStartingWith(String prefix);
}
