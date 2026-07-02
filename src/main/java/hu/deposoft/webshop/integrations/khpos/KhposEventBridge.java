package hu.deposoft.webshop.integrations.khpos;

import hu.deposoft.khpos.starter.event.KhposChallengeRequiredEvent;
import hu.deposoft.khpos.starter.event.KhposPaymentConfirmedEvent;
import hu.deposoft.khpos.starter.event.KhposPaymentEvent;
import hu.deposoft.khpos.starter.event.KhposPaymentPendingEvent;
import hu.deposoft.khpos.starter.event.KhposPaymentRefundedEvent;
import hu.deposoft.khpos.starter.event.KhposPaymentRejectedEvent;
import hu.deposoft.khpos.starter.event.KhposPaymentReversedEvent;
import hu.deposoft.khpos.starter.event.KhposPaymentSettledEvent;
import hu.deposoft.webshop.application.payment.PaymentGateway.ResultKind;
import hu.deposoft.webshop.application.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;

/**
 * Inbound adapter: the starter's signature-verified bank-return events are routed
 * into the single idempotent {@link PaymentService#applyGatewayResult} entry
 * point. Duplicate callbacks are therefore no-ops by construction.
 */
@Slf4j
@RequiredArgsConstructor
public class KhposEventBridge {

    private final PaymentService paymentService;

    @EventListener
    public void on(KhposPaymentEvent event) {
        switch (event) {
            case KhposPaymentConfirmedEvent e ->
                    paymentService.applyGatewayResult(e.payId(), ResultKind.CONFIRMED, e.resultMessage());
            case KhposPaymentSettledEvent e ->
                    paymentService.applyGatewayResult(e.payId(), ResultKind.CONFIRMED, e.resultMessage());
            case KhposPaymentRejectedEvent e ->
                    paymentService.applyGatewayResult(e.payId(), ResultKind.REJECTED, e.resultMessage());
            case KhposPaymentReversedEvent e ->
                    paymentService.applyGatewayResult(e.payId(), ResultKind.REVERSED, e.resultMessage());
            case KhposPaymentPendingEvent e ->
                    paymentService.applyGatewayResult(e.payId(), ResultKind.PENDING, e.resultMessage());
            case KhposPaymentRefundedEvent e ->
                    // refunds belong to the admin/sztornó flow (T16) — log only for now
                    log.info("Refund event for payId={} order={} ignored until T16", e.payId(), e.orderNo());
            case KhposChallengeRequiredEvent e ->
                    log.info("3DS challenge in progress for payId={}", e.payId());
        }
    }
}
