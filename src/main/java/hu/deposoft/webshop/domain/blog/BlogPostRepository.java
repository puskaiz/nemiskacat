package hu.deposoft.webshop.domain.blog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {
    Optional<BlogPost> findBySlug(String slug);
    boolean existsBySlug(String slug);
    Page<BlogPost> findByStatus(PublicationStatus status, Pageable pageable);
    Page<BlogPost> findByStatusAndCategories_Slug(PublicationStatus status, String categorySlug, Pageable pageable);
}
