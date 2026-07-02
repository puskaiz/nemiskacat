package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refund reconciliation safety net (H3): for CONFIRMED payments on still-open (PAID/PACKING)
 * orders, asks the gateway whether the payment was already refunded out-of-band (or by a
 * refund whose commit was lost) and heals the DB to match — WITHOUT ever calling
 * {@code gateway.refund}, so it can never initiate a refund. Mirrors
 * {@code PaymentService.recheckPending}; the scheduled loop lives in
 * {@code RefundReconciliationScheduler}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefundReconciliationService {

    public enum Outcome { HEALED, ALERTED, NONE }

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final RefundFinalizer finalizer;

    /** Payment ids worth re-checking now (CONFIRMED, on PAID/PACKING orders, stale enough). */
    @Transactional(readOnly = true)
    public List<Long> findCandidatePaymentIds(Duration maxAge) {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minus(maxAge);
        return payments.findReconcilable(
                        Payment.State.CONFIRMED, List.of(OrderStatus.PAID, OrderStatus.PACKING), cutoff)
                .stream().map(Payment::getId).toList();
    }

    /** Reconcile one payment against the gateway. Own transaction; never throws for the caller. */
    // NB: own transaction per call — the scheduler MUST invoke this from a separate bean
    // (RefundReconciliationScheduler) so @Transactional applies and one failure can't abort the batch.
    @Transactional
    public Outcome reconcileOne(Long paymentId) {
        Payment payment = payments.findById(paymentId).orElse(null);
        if (payment == null) {
            return Outcome.NONE;
        }
        payment.touchChecked(OffsetDateTime.now(ZoneOffset.UTC));
        if (payment.getState() != Payment.State.CONFIRMED) {
            return Outcome.NONE; // state changed since it was listed
        }
        Order order = orders.findByIdForUpdate(payment.getOrder().getId()).orElseThrow();
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.PAID && status != OrderStatus.PACKING) {
            return Outcome.NONE; // already REFUNDED, or moved on to SHIPPED/etc.
        }

        PaymentGateway.Refundability refundability;
        try {
            refundability = gateway.refundability(payment.getPayId());
        } catch (RuntimeException e) {
            log.warn("Reconcile status query failed for payId={}: {}", payment.getPayId(), e.getMessage());
            return Outcome.NONE; // transient; re-checked next sweep
        }

        return switch (refundability) {
            case ALREADY_REFUNDED -> heal(order, payment);
            case REFUND_IN_PROGRESS -> {
                log.info("Reconcile: refund in progress for order {}, will re-check", order.orderNumber());
                yield Outcome.NONE;
            }
            case REFUNDABLE -> Outcome.NONE; // normal active order — nothing to reconcile
            case NOT_REFUNDABLE -> {
                payment.raiseAlert("Reconcile: CONFIRMED payment " + payment.getPayId() + " for order "
                        + order.orderNumber() + " is NOT_REFUNDABLE at gateway — inconsistency");
                log.error("ALERT: CONFIRMED payment {} reported NOT_REFUNDABLE by gateway — manual review needed",
                        payment.getPayId());
                yield Outcome.ALERTED;
            }
        };
    }

    private Outcome heal(Order order, Payment payment) {
        try {
            finalizer.finalizeRefund(order, payment, "reconciled: already refunded at gateway", true);
            return Outcome.HEALED;
        } catch (RuntimeException e) {
            payment.raiseAlert("Reconcile: gateway shows payment " + payment.getPayId() + " refunded but order "
                    + order.orderNumber() + " could not be finalized: " + e.getMessage());
            log.error("ALERT: reconcile could not finalize refunded order {} — manual intervention needed",
                    order.orderNumber());
            return Outcome.ALERTED;
        }
    }
}
