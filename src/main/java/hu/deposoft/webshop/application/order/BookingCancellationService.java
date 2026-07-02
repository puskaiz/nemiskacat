package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.application.invoicing.InvoicingService;
import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.domain.order.OrderItemRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancel a single order line (T16 slice 2b-2) — the workshop-booking case: partially
 * refund the line via the gateway, mark the line cancelled (freeing its seats), audit
 * it, and issue a line-scoped per-source credit note. The Payment stays CONFIRMED and
 * the order status is unchanged (other lines may remain). Line-generic; the workshop
 * attendee list is the entry point. Pre-shipment only (PAID/PACKING).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BookingCancellationService {

    private final OrderItemRepository orderItems;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final InvoicingService invoicing;
    private final AuditService audit;

    /** Policy rejection (wrong order state / no confirmed payment). */
    public static class BookingCancelNotAllowedException extends RuntimeException {
        public BookingCancelNotAllowedException(String message) {
            super(message);
        }
    }

    /** The gateway refused or failed the partial refund. */
    public static class BookingRefundFailedException extends RuntimeException {
        public BookingRefundFailedException(String message) {
            super(message);
        }
    }

    @Transactional
    public long cancelBooking(Long orderItemId) {
        OrderItem line = orderItems.findByIdForUpdate(orderItemId)
                .orElseThrow(() -> new OrderAdminQueryService.NotFoundException("No order item " + orderItemId));
        if (line.getCancelledQuantity() >= line.getQuantity()) {
            return 0L; // idempotent — already fully cancelled
        }
        Order order = line.getOrder();
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.PAID && status != OrderStatus.PACKING) {
            throw new BookingCancelNotAllowedException(
                    "Only PAID/PACKING orders can have a booking cancelled here; this one is " + status);
        }
        Payment payment = payments.findFirstByOrderOrderByIdDesc(order)
                .orElseThrow(() -> new BookingCancelNotAllowedException("No payment to refund for order " + order.getId()));
        if (payment.getState() != Payment.State.CONFIRMED) {
            throw new BookingCancelNotAllowedException(
                    "Payment for order " + order.getId() + " is " + payment.getState() + ", not CONFIRMED");
        }

        long amount = line.getLineGrossHuf();
        PaymentGateway.RefundResult result;
        try {
            result = gateway.refund(payment.getPayId(), amount);
        } catch (RuntimeException e) {
            throw new BookingRefundFailedException("Refund call failed: " + e.getMessage());
        }
        if (!result.success()) {
            throw new BookingRefundFailedException("Refund declined: " + result.message());
        }

        line.cancelWholeLine();
        audit.record("BOOKING_CANCELLED", "order_item", String.valueOf(orderItemId),
                order.orderNumber() + " " + line.getSku() + " x" + line.getQuantity()
                        + " refunded " + amount + " HUF");
        log.info("Booking line {} of order {} cancelled ({} HUF)", orderItemId, order.orderNumber(), amount);

        invoicing.creditNoteForLine(line); // line-scoped per-source storno (gated; never rolls back the refund)
        return amount;
    }
}
