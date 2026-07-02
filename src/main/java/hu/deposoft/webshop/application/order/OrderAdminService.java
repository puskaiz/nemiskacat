package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-driven order status transitions (T16 slice 2a): the daily fulfilment
 * flow. Applies an admin policy on top of the domain state machine and audits
 * every change. No money movement — paid-order cancel + refund is slice 2b.
 */
@Service
@RequiredArgsConstructor
public class OrderAdminService {

    private final OrderRepository orders;
    private final AuditService audit;

    /** Raised when a transition is not permitted (admin policy or domain sequence). */
    public static class TransitionNotAllowedException extends RuntimeException {
        public TransitionNotAllowedException(String message) {
            super(message);
        }
    }

    @Transactional
    public void transition(Long orderId, OrderStatus target) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> new OrderAdminQueryService.NotFoundException("No order " + orderId));
        OrderStatus current = order.getStatus();

        // admin policy (before the domain gate)
        if (target == OrderStatus.PAID) {
            throw new TransitionNotAllowedException("PAID is set by payment, not by the admin");
        }
        if (target == OrderStatus.CANCELLED && current != OrderStatus.NEW) {
            throw new TransitionNotAllowedException(
                    "Fizetett rendelés lemondásához visszatérítés szükséges, ami egyelőre nem elérhető.");
        }

        try {
            order.transitionTo(target); // domain gate enforces the legal sequence
        } catch (IllegalStateException e) {
            throw new TransitionNotAllowedException(e.getMessage());
        }
        audit.record("ORDER_STATUS_CHANGE", "order", String.valueOf(orderId), current + "→" + target);
    }
}
