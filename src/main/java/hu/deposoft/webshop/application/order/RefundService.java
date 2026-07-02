package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Whole-order, pre-shipment refund (T16 slice 2b-1): refund the money via the
 * gateway, mark the payment reversed, move the order to REFUNDED, audit it, and
 * issue the per-source storno invoice. Idempotent; refunds only PAID/PACKING.
 * Before charging it asks the gateway whether the payment is still refundable
 * (H2): if a prior refund already landed (e.g. a crash after the gateway call
 * but before commit), it heals the DB without a second charge.
 */
@Service
@RequiredArgsConstructor
public class RefundService {

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final RefundFinalizer finalizer;

    /** Policy rejection (wrong state / no confirmed payment). */
    public static class RefundNotAllowedException extends RuntimeException {
        public RefundNotAllowedException(String message) {
            super(message);
        }
    }

    /** The gateway refused or failed the refund. */
    public static class RefundFailedException extends RuntimeException {
        public RefundFailedException(String message) {
            super(message);
        }
    }

    @Transactional
    public void refund(Long orderId) {
        Order order = orders.findByIdForUpdate(orderId)
                .orElseThrow(() -> new hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException("No order " + orderId));
        OrderStatus current = order.getStatus();
        if (current == OrderStatus.REFUNDED) {
            return; // idempotent — already refunded
        }
        if (current != OrderStatus.PAID && current != OrderStatus.PACKING) {
            throw new RefundNotAllowedException("Only PAID/PACKING orders can be refunded here; this one is " + current);
        }
        if (order.getItems().stream().anyMatch(i -> i.getCancelledQuantity() > 0)) {
            throw new RefundNotAllowedException(
                    "Order " + orderId + " has cancelled bookings; refund those lines individually instead of the whole order");
        }
        Payment payment = payments.findFirstByOrderOrderByIdDesc(order)
                .orElseThrow(() -> new RefundNotAllowedException("No payment to refund for order " + orderId));
        if (payment.getState() != Payment.State.CONFIRMED) {
            throw new RefundNotAllowedException("Payment for order " + orderId + " is " + payment.getState() + ", not CONFIRMED");
        }

        // KHPos is authoritative on refund state per payId, so ask before charging:
        // a prior refund that succeeded then lost its commit (crash/timeout) leaves the
        // order PAID, so we re-enter here — and now skip the second gateway call.
        // refundability() is intentionally uncaught: a gateway error must roll back
        // (order stays PAID for a later retry), not heal on an unknown refund state.
        String resultMessage = switch (gateway.refundability(payment.getPayId())) {
            case ALREADY_REFUNDED -> "healed: already refunded at gateway";
            case REFUNDABLE -> {
                PaymentGateway.RefundResult result;
                try {
                    result = gateway.refund(payment.getPayId(), order.getTotalGrossHuf());
                } catch (RuntimeException e) {
                    throw new RefundFailedException("Refund call failed: " + e.getMessage());
                }
                if (!result.success()) {
                    throw new RefundFailedException("Refund declined: " + result.message());
                }
                yield result.message();
            }
            case REFUND_IN_PROGRESS -> throw new RefundFailedException(
                    "Refund already in progress at gateway for payment " + payment.getPayId()
                            + "; awaiting settlement before the order can be finalized");
            case NOT_REFUNDABLE -> throw new RefundFailedException(
                    "Gateway reports payment " + payment.getPayId() + " is not refundable");
        };

        finalizer.finalizeRefund(order, payment, resultMessage, false);
    }
}
