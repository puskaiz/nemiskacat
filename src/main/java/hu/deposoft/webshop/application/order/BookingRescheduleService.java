package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.application.catalog.AvailabilityService;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.domain.order.OrderItemRepository;
import hu.deposoft.webshop.domain.workshop.WorkshopSession;
import hu.deposoft.webshop.domain.workshop.WorkshopSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reschedule a workshop booking (T16 slice 2b-3): move an order line to another
 * session of the SAME workshop at the SAME price. No money moves — the seat frees
 * on the source variant and is consumed on the target automatically (availability
 * is per-variant). Order status, payment, and invoices are untouched.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookingRescheduleService {

    private final OrderItemRepository orderItems;
    private final WorkshopSessionRepository sessions;
    private final AvailabilityService availability;
    private final AuditService audit;

    /** Policy rejection (wrong state / different workshop / different price / no capacity / no-op). */
    public static class RescheduleNotAllowedException extends RuntimeException {
        public RescheduleNotAllowedException(String message) {
            super(message);
        }
    }

    @Transactional
    public void reschedule(Long orderItemId, Long targetSessionId) {
        OrderItem line = orderItems.findById(orderItemId)
                .orElseThrow(() -> new OrderAdminQueryService.NotFoundException("No order item " + orderItemId));
        WorkshopSession target = sessions.findById(targetSessionId)
                .orElseThrow(() -> new WorkshopService.NotFoundException("No session " + targetSessionId));
        Variant targetSeat = target.getVariant();
        Variant sourceSeat = line.getVariant();

        Order order = line.getOrder();
        // reschedule is allowed pre-fulfilment (NEW/PAID/PACKING); it moves no money
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.CANCELLED || status == OrderStatus.REFUNDED
                || status == OrderStatus.SHIPPED || status == OrderStatus.COMPLETED) {
            throw new RescheduleNotAllowedException("Order " + order.getId() + " is " + status + "; cannot reschedule");
        }
        if (line.getCancelledQuantity() >= line.getQuantity()) {
            throw new RescheduleNotAllowedException("Line " + orderItemId + " is cancelled");
        }
        WorkshopSession sourceSession = sessions.findByVariantId(sourceSeat.getId())
                .orElseThrow(() -> new RescheduleNotAllowedException(
                        "Line " + orderItemId + " is not a workshop booking"));
        if (!targetSeat.getProduct().getId().equals(sourceSeat.getProduct().getId())) {
            throw new RescheduleNotAllowedException("Target session belongs to a different workshop");
        }
        if (targetSeat.getId().equals(sourceSeat.getId())) {
            throw new RescheduleNotAllowedException("Target session is the current one");
        }
        Long targetPrice = targetSeat.getRegularPriceHuf();
        if (targetPrice == null || targetPrice != line.getUnitGrossHuf()) {
            throw new RescheduleNotAllowedException("Target session price differs; price-delta reschedule is not supported");
        }
        int needed = line.getQuantity() - line.getCancelledQuantity();
        if (availability.availableQty(targetSeat, AvailabilityService.NO_CART) < needed) {
            throw new RescheduleNotAllowedException("Target session has no free seat");
        }

        String oldSku = line.getSku();
        String oldDate = sourceSession.getStartAt().toString();
        line.moveToSeat(targetSeat);
        audit.record("BOOKING_RESCHEDULED", "order_item", String.valueOf(orderItemId),
                order.orderNumber() + " " + oldSku + "→" + targetSeat.getSku()
                        + " (" + oldDate + "→" + target.getStartAt() + ")");
        log.info("Booking line {} of order {} rescheduled {} → {}", orderItemId, order.orderNumber(),
                oldSku, targetSeat.getSku());
    }
}
