package hu.deposoft.webshop.domain.blog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BlogCategoryRepository extends JpaRepository<BlogCategory, Long> {
    Optional<BlogCategory> findBySlug(String slug);
    boolean existsBySlug(String slug);
    List<BlogCategory> findAllByOrderByNameAsc();
    List<BlogCategory> findBySidebarHiddenFalseOrderByNameAsc();
}
