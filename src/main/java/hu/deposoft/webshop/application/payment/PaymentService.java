package hu.deposoft.webshop.application.payment;

import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Payment lifecycle (T10). {@link #applyGatewayResult} is the single idempotent
 * entry point for every gateway outcome — bank-return events and the timed
 * re-check both land here, so duplicate callbacks are no-ops and a late callback
 * cannot double-transition the order. The "paid but order recording failed"
 * branch raises an alert flag (ops alerting hooks in at T23).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final org.springframework.context.ApplicationEventPublisher events;

    public record StartResult(String payId, String redirectUrl) {
    }

    public static class PaymentNotAllowedException extends RuntimeException {
        public PaymentNotAllowedException(String message) {
            super(message);
        }
    }

    /** False while the gateway has no key material (khpos.enabled=false). */
    public boolean isPaymentEnabled() {
        return gateway.isEnabled();
    }

    /** Starts a new payment attempt at the gateway for a NEW order. */
    @Transactional
    public StartResult start(Order order, String returnUrl) {
        if (order.getStatus() != OrderStatus.NEW) {
            throw new PaymentNotAllowedException(
                    "Order %s is %s — payment is only allowed for NEW orders"
                            .formatted(order.orderNumber(), order.getStatus()));
        }
        PaymentGateway.InitResult init = gateway.initPayment(order, order.getTotalGrossHuf(), returnUrl);
        payments.save(Payment.initiate(order, init.payId(), order.getTotalGrossHuf()));
        log.info("Payment {} initiated for order {} ({} HUF)",
                init.payId(), order.orderNumber(), order.getTotalGrossHuf());
        return new StartResult(init.payId(), init.redirectUrl());
    }

    /** The single idempotent handler for every gateway outcome. */
    @Transactional
    public void applyGatewayResult(String payId, PaymentGateway.ResultKind kind, String message) {
        Optional<Payment> found = payments.findByPayId(payId);
        if (found.isEmpty()) {
            // money may have moved at the bank with no record on our side — loudest possible log
            log.error("ALERT: gateway result {} for unknown payId={} ({}) — manual reconciliation needed",
                    kind, payId, message);
            return;
        }
        Payment payment = found.get();
        if (payment.isTerminal()) {
            log.info("Duplicate gateway result {} for payId={} ignored (state={})",
                    kind, payId, payment.getState());
            return;
        }
        switch (kind) {
            case CONFIRMED -> confirm(payment, message);
            case REJECTED -> payment.markState(Payment.State.FAILED, message);
            case REVERSED -> payment.markState(Payment.State.REVERSED, message);
            case PENDING -> log.info("Payment {} still pending: {}", payId, message);
        }
    }

    private void confirm(Payment payment, String message) {
        payment.markState(Payment.State.CONFIRMED, message);
        Order order = payment.getOrder();
        if (order.getStatus() == OrderStatus.PAID) {
            return; // another attempt already paid the order
        }
        try {
            order.transitionTo(OrderStatus.PAID);
            log.info("Order {} marked PAID by payment {}", order.orderNumber(), payment.getPayId());
            events.publishEvent(new hu.deposoft.webshop.application.invoicing.OrderPaidEvent(order.getId()));
        } catch (IllegalStateException e) {
            // the bank took the money but the order cannot take it — "fizetve, de rögzítés hibázott"
            payment.raiseAlert("Paid, but order %s is %s: %s"
                    .formatted(order.orderNumber(), order.getStatus(), message));
            log.error("ALERT: payment {} CONFIRMED but order {} is {} — manual intervention needed",
                    payment.getPayId(), order.orderNumber(), order.getStatus());
        }
    }

    /**
     * Missing-callback safety net: queries the gateway for payments still
     * INITIATED and older than {@code minAge}, and applies the result through the
     * same idempotent handler. Scheduled wiring lives in config.
     */
    @Transactional
    public int recheckPending(Duration minAge) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Payment> pending = payments.findByStateAndCreatedAtBefore(
                Payment.State.INITIATED, now.minus(minAge));
        for (Payment payment : pending) {
            payment.touchChecked(now);
            try {
                PaymentGateway.StatusResult status = gateway.queryStatus(payment.getPayId());
                applyGatewayResult(payment.getPayId(), status.kind(), status.message());
            } catch (RuntimeException e) {
                log.warn("Status re-check failed for payId={}: {}", payment.getPayId(), e.getMessage());
            }
        }
        return pending.size();
    }
}
