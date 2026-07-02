package hu.deposoft.webshop.domain.checkout;

import java.util.Map;
import java.util.Set;

/**
 * Order state machine (TERV §5/M6): NEW -> PAID -> PACKING -> SHIPPED -> COMPLETED,
 * with CANCELLED reachable from NEW and PAID only, and REFUNDED reachable from
 * PAID/PACKING (whole-order pre-shipment refund). CANCELLED/COMPLETED/REFUNDED
 * are terminal.
 *
 * <p>PROCESSING, ON_HOLD, FAILED, AWAITING_SHIPMENT are import-only states from
 * WooCommerce (mapped 1:1, lossless). They are terminal in the native state machine
 * — historical orders only, never created by the webshop checkout flow.
 */
public enum OrderStatus {
    NEW, PAID, PACKING, SHIPPED, COMPLETED, CANCELLED, REFUNDED,
    // Import-only states from WooCommerce (1:1, lossless). Terminal in the
    // native checkout state machine — historical orders only.
    PROCESSING, ON_HOLD, FAILED, AWAITING_SHIPMENT;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.ofEntries(
            Map.entry(NEW, Set.of(PAID, CANCELLED)),
            Map.entry(PAID, Set.of(PACKING, CANCELLED, REFUNDED)),
            Map.entry(PACKING, Set.of(SHIPPED, REFUNDED)),
            Map.entry(SHIPPED, Set.of(COMPLETED)),
            Map.entry(COMPLETED, Set.of()),
            Map.entry(CANCELLED, Set.of()),
            Map.entry(REFUNDED, Set.of()),
            Map.entry(PROCESSING, Set.of()),
            Map.entry(ON_HOLD, Set.of()),
            Map.entry(FAILED, Set.of()),
            Map.entry(AWAITING_SHIPMENT, Set.of()));

    public boolean canTransitionTo(OrderStatus target) {
        return target != null && ALLOWED.get(this).contains(target);
    }
}
