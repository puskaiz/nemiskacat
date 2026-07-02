package hu.deposoft.webshop.integrations.khpos;

import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.domain.order.Order;

/**
 * Fallback while khpos.enabled=false (the [EMBER] staging keys do not exist yet).
 * The checkout hides the pay button; a direct call fails loudly.
 */
public class DisabledPaymentGateway implements PaymentGateway {

    public static class PaymentUnavailableException extends RuntimeException {
        public PaymentUnavailableException() {
            super("Card payment is not configured (khpos.enabled=false)");
        }
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public InitResult initPayment(Order order, long amountHuf, String returnUrl) {
        throw new PaymentUnavailableException();
    }

    @Override
    public StatusResult queryStatus(String payId) {
        throw new PaymentUnavailableException();
    }

    @Override
    public RefundResult refund(String payId, long amountHuf) {
        throw new PaymentUnavailableException();
    }

    @Override
    public Refundability refundability(String payId) {
        throw new PaymentUnavailableException();
    }
}
