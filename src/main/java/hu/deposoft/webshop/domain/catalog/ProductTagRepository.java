package hu.deposoft.webshop.domain.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProductTagRepository extends JpaRepository<ProductTag, Long> {
    Optional<ProductTag> findByExternalId(Long externalId);
    boolean existsBySlug(String slug);
    java.util.List<ProductTag> findAllByOrderByNameAsc();
}
