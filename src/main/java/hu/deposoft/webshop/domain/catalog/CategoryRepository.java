package hu.deposoft.webshop.domain.catalog;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByExternalId(Long externalId);

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Category> findAllByOrderByNameAsc();

    /** Top-level categories (no parent), ordered by the provided Sort. */
    List<Category> findByParentIsNull(Sort sort);
}
