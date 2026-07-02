package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.checkout.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPayId(String payId);

    Optional<Payment> findFirstByOrderOrderByIdDesc(Order order);

    /** Candidates for the missing-callback re-check: still open and started before the cutoff. */
    List<Payment> findByStateAndCreatedAtBefore(Payment.State state, OffsetDateTime cutoff);

    /**
     * Reconciliation candidates (H3): payments in the given state on orders in the given
     * statuses, not re-checked since the cutoff. Used to find open paid orders whose
     * gateway-side refund state may have changed.
     */
    @Query("""
            select p from Payment p
            where p.state = :state
              and p.order.status in :statuses
              and (p.lastCheckedAt is null or p.lastCheckedAt < :cutoff)
            """)
    List<Payment> findReconcilable(@Param("state") Payment.State state,
                                   @Param("statuses") Collection<OrderStatus> statuses,
                                   @Param("cutoff") OffsetDateTime cutoff);
}
