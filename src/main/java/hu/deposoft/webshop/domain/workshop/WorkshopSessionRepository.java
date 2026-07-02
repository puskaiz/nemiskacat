package hu.deposoft.webshop.domain.workshop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkshopSessionRepository extends JpaRepository<WorkshopSession, Long> {

    Optional<WorkshopSession> findByVariantId(Long variantId);

    List<WorkshopSession> findByVariant_ProductIdOrderByStartAtAsc(Long productId);
}
