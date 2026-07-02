package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.catalog.Variant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByVariantAndCartToken(Variant variant, String cartToken);

    /**
     * Ledger input: live holds of OTHER carts. Callers without a token pass ""
     * (never a valid token), so every live reservation counts as foreign.
     */
    @Query("""
            SELECT COALESCE(SUM(r.quantity), 0) FROM Reservation r
            WHERE r.variant.id = :variantId
              AND r.expiresAt > :now
              AND r.cartToken <> :ownToken
            """)
    int activeForeignQuantity(@Param("variantId") Long variantId,
                              @Param("now") OffsetDateTime now,
                              @Param("ownToken") String ownToken);

    @Modifying
    void deleteByCartToken(String cartToken);
}
