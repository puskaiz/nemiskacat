package hu.deposoft.webshop.domain.page;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ContentPageRepository extends JpaRepository<ContentPage, Long> {
    Optional<ContentPage> findBySlug(String slug);
    Optional<ContentPage> findByExternalId(Long externalId);
    boolean existsBySlug(String slug);
}
