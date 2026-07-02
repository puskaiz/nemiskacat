package hu.deposoft.webshop.integrations.khpos;

import hu.deposoft.khpos.core.dto.CartItem;
import hu.deposoft.khpos.core.dto.KhposInitRequest;
import hu.deposoft.khpos.core.dto.KhposInitResult;
import hu.deposoft.khpos.core.dto.KhposStatusResult;
import hu.deposoft.khpos.core.model.Currency;
import hu.deposoft.khpos.starter.autoconfig.KhposProperties;
import hu.deposoft.khpos.starter.service.KhposPaymentService;
import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.domain.order.Order;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * PaymentGateway port implementation on the in-house KHPos starter. Active only
 * when khpos.enabled=true (needs the [EMBER] key material); otherwise
 * {@link DisabledPaymentGateway} takes its place.
 */
@RequiredArgsConstructor
public class KhposGatewayAdapter implements PaymentGateway {

    private final KhposPaymentService khpos;
    private final KhposProperties properties;

    @Override
    public InitResult initPayment(Order order, long amountHuf, String returnUrl) {
        String merchantId = merchantId();
        // Bank orderNo: numeric, max 10 digits, no leading zero (wiki rule) — our order id fits.
        String orderNo = String.valueOf(order.getId());
        KhposInitResult result = khpos.initPayment(KhposInitRequest.builder()
                .merchantId(merchantId)
                .orderNo(orderNo)
                .totalAmount(amountHuf)
                .currency(Currency.HUF)
                .returnUrl(URI.create(returnUrl))
                // bank cart is limited to 1–2 lines: send a single summary line for the order
                .cart(List.of(new CartItem("Rendelés " + order.orderNumber(), 1, amountHuf, null)))
                .language("hu")
                // merchantData is echoed back on the bank callback — the starter's return
                // controller reads merchantId from here to re-query the payment status
                .merchantData(Map.of("merchantId", merchantId, "orderNo", orderNo, "currency", "HUF"))
                .build());
        return new InitResult(result.payId(),
                result.redirectUrl() == null ? null : result.redirectUrl().toString());
    }

    @Override
    public StatusResult queryStatus(String payId) {
        KhposStatusResult result = khpos.queryStatus(merchantId(), payId);
        return new StatusResult(mapStatus(result), result.resultMessage());
    }

    @Override
    public RefundResult refund(String payId, long amountHuf) {
        KhposStatusResult result = khpos.cancelOrReturn(merchantId(), payId, amountHuf);
        ResultKind kind = mapStatus(result);
        boolean success = kind == ResultKind.CONFIRMED || kind == ResultKind.REVERSED;
        return new RefundResult(success, result.resultMessage());
    }

    @Override
    public Refundability refundability(String payId) {
        KhposStatusResult result = khpos.queryStatus(merchantId(), payId);
        return switch (result.status()) {
            case REVERSED, RETURNED -> Refundability.ALREADY_REFUNDED; // terminal: refund settled
            case REFUND_PROCESSING -> Refundability.REFUND_IN_PROGRESS; // in flight, not yet settled
            // WAITING_SETTLEMENT is reversible (refund() calls cancelOrReturn), so it counts as
            // refundable here even though the starter's canRefund() is true only once SETTLED.
            case CONFIRMED, SETTLED, WAITING_SETTLEMENT -> Refundability.REFUNDABLE;
            case FAILED, REJECTED, CREATED, INITIATED -> Refundability.NOT_REFUNDABLE;
        };
    }

    private String merchantId() {
        return properties.merchants().get(Currency.HUF).id();
    }

    private ResultKind mapStatus(KhposStatusResult result) {
        return switch (result.status()) {
            case CONFIRMED, WAITING_SETTLEMENT, SETTLED,
                 REFUND_PROCESSING, RETURNED -> ResultKind.CONFIRMED; // money was taken
            case FAILED, REJECTED -> ResultKind.REJECTED;
            case REVERSED -> ResultKind.REVERSED;
            case CREATED, INITIATED -> ResultKind.PENDING;
        };
    }
}
