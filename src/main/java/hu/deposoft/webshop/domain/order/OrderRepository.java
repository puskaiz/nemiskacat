package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.checkout.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Lock the order row for the duration of a money operation (refund) — serializes concurrent refunds. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    Optional<Order> findByClientKey(String clientKey);

    Optional<Order> findByWooOrderId(Long wooOrderId);

    /** Confirmation-page load: items fetched eagerly (templates render outside the tx). */
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.clientKey = :clientKey")
    Optional<Order> findWithItemsByClientKey(@Param("clientKey") String clientKey);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findWithItemsById(@Param("id") Long id);

    /** A customer's order history (linked by email), items fetched for rendering. */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE lower(o.email) = lower(:email) ORDER BY o.createdAt DESC")
    java.util.List<Order> findByEmailWithItems(@Param("email") String email);

    /** Ledger input: quantity ordered for a variant since the last stock sync (cancelled excluded). */
    @Query("""
            SELECT COALESCE(SUM(oi.quantity - oi.cancelledQuantity), 0) FROM OrderItem oi
            WHERE oi.variant.id = :variantId
              AND oi.order.status <> :excluded
              AND oi.order.createdAt > :since
            """)
    int orderedQuantitySince(@Param("variantId") Long variantId,
                             @Param("since") OffsetDateTime since,
                             @Param("excluded") OrderStatus excluded);

    /** Total quantity ordered for a variant across ALL order statuses (used to guard FK-breaking deletes). */
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi WHERE oi.variant.id = :variantId")
    int totalOrderedQuantity(@Param("variantId") Long variantId);

    /** Bookings (non-cancelled order lines) for a workshop's session seats — admin attendee list. */
    @Query("""
            SELECT oi FROM OrderItem oi
            JOIN FETCH oi.order o
            WHERE oi.variant.product.id = :workshopId AND o.status <> :excluded
            ORDER BY o.createdAt
            """)
    java.util.List<OrderItem> findWorkshopBookings(@Param("workshopId") Long workshopId,
                                                   @Param("excluded") OrderStatus excluded);

    /**
     * Admin order search: all filters nullable; matches customer name and email by substring.
     *
     * <p>Native query rationale: PostgreSQL cannot infer the type of a bare {@code null} bind
     * for the {@code :from}/{@code :to} parameters in a JPQL {@code :p IS NULL OR ...} filter,
     * raising "could not determine data type of parameter". Using a native query with
     * {@code CAST(:from AS timestamptz)} makes the type explicit. The {@code countQuery} must
     * be kept in sync with the main WHERE clause whenever filters are changed.
     */
    @Query(value = """
            SELECT * FROM orders o
            WHERE (:status IS NULL OR o.status = :status)
              AND (CAST(:from AS timestamptz) IS NULL OR o.created_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR o.created_at <= CAST(:to AS timestamptz))
              AND (CAST(:q AS text) IS NULL OR lower(o.customer_name) LIKE lower(concat('%', CAST(:q AS text), '%'))
                              OR lower(o.email) LIKE lower(concat('%', CAST(:q AS text), '%')))
            ORDER BY o.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM orders o
            WHERE (:status IS NULL OR o.status = :status)
              AND (CAST(:from AS timestamptz) IS NULL OR o.created_at >= CAST(:from AS timestamptz))
              AND (CAST(:to AS timestamptz) IS NULL OR o.created_at <= CAST(:to AS timestamptz))
              AND (CAST(:q AS text) IS NULL OR lower(o.customer_name) LIKE lower(concat('%', CAST(:q AS text), '%'))
                              OR lower(o.email) LIKE lower(concat('%', CAST(:q AS text), '%')))
            """,
            nativeQuery = true)
    Page<Order> search(@Param("status") String status,
                       @Param("from") OffsetDateTime from,
                       @Param("to") OffsetDateTime to,
                       @Param("q") String q,
                       Pageable pageable);
}
