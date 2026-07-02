package hu.deposoft.webshop.application.payment;

import hu.deposoft.webshop.domain.order.Order;

/**
 * Port to the card payment gateway (dependency inversion: the application layer
 * owns the contract, integrations/khpos implements it on the starter). Amounts in
 * HUF minor units (= forint).
 */
public interface PaymentGateway {

    /** Normalized outcome of a gateway interaction. */
    enum ResultKind {CONFIRMED, REJECTED, REVERSED, PENDING}

    record InitResult(String payId, String redirectUrl) {
    }

    record StatusResult(ResultKind kind, String message) {
    }

    record RefundResult(boolean success, String message) {
    }

    /**
     * Whether a confirmed payment can still be refunded — the authoritative
     * idempotency check, since KHPos tracks refund state per payId.
     * {@code REFUND_IN_PROGRESS} = a refund initiated at the gateway but not yet
     * settled (terminal refund states are {@code ALREADY_REFUNDED}).
     */
    enum Refundability { REFUNDABLE, ALREADY_REFUNDED, REFUND_IN_PROGRESS, NOT_REFUNDABLE }

    /** False while the gateway has no key material — the checkout hides the pay button. */
    default boolean isEnabled() {
        return true;
    }

    /** @param returnUrl absolute URL the bank redirects the customer back to (the starter's callback). */
    InitResult initPayment(Order order, long amountHuf, String returnUrl);

    StatusResult queryStatus(String payId);

    /** Refund (or reverse) a confirmed payment. Amount in HUF. */
    RefundResult refund(String payId, long amountHuf);

    /** Ask the gateway whether {@code payId} can still be refunded (vs. already refunded). */
    Refundability refundability(String payId);
}
