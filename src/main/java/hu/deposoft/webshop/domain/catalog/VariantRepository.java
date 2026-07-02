package hu.deposoft.webshop.domain.catalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface VariantRepository extends JpaRepository<Variant, Long> {

    Optional<Variant> findByExternalId(Long externalId);

    Optional<Variant> findByProductAndIsDefaultTrue(Product product);

    Optional<Variant> findBySku(String sku);

    /**
     * Locks variant rows for order placement (overselling protection point 3 of 3).
     * Ordered by id so concurrent placements acquire locks in the same sequence
     * (no deadlock).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Variant v WHERE v.id IN :ids ORDER BY v.id")
    List<Variant> lockAllByIdIn(@Param("ids") Collection<Long> ids);
}
