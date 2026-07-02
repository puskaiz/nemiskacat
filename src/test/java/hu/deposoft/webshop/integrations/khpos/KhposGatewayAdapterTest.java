package hu.deposoft.webshop.integrations.khpos;

import hu.deposoft.khpos.core.dto.KhposStatusResult;
import hu.deposoft.khpos.core.model.Currency;
import hu.deposoft.khpos.core.model.PaymentStatus;
import hu.deposoft.khpos.starter.autoconfig.KhposProperties;
import hu.deposoft.khpos.starter.service.KhposPaymentService;
import hu.deposoft.webshop.application.payment.PaymentGateway.Refundability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KhposGatewayAdapterTest {

    private final KhposPaymentService khpos = mock(KhposPaymentService.class);
    private final KhposProperties properties = mock(KhposProperties.class);
    private KhposGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        when(properties.merchants()).thenReturn(
                Map.of(Currency.HUF, new KhposProperties.Merchant("M-1", null, null)));
        adapter = new KhposGatewayAdapter(khpos, properties);
    }

    private void gatewayReports(PaymentStatus status) {
        when(khpos.queryStatus("M-1", "PAY-1"))
                .thenReturn(new KhposStatusResult("PAY-1", status, 0, "ok", null, null));
    }

    @Test
    void alreadyRefundedWhenReversedOrReturned() {
        for (PaymentStatus s : new PaymentStatus[]{
                PaymentStatus.REVERSED, PaymentStatus.RETURNED}) {
            gatewayReports(s);
            assertThat(adapter.refundability("PAY-1")).as("%s", s)
                    .isEqualTo(Refundability.ALREADY_REFUNDED);
        }
    }

    @Test
    void refundInProgressWhenProcessing() {
        gatewayReports(PaymentStatus.REFUND_PROCESSING);
        assertThat(adapter.refundability("PAY-1")).isEqualTo(Refundability.REFUND_IN_PROGRESS);
    }

    @Test
    void refundableWhenConfirmedSettledOrWaiting() {
        for (PaymentStatus s : new PaymentStatus[]{
                PaymentStatus.CONFIRMED, PaymentStatus.SETTLED, PaymentStatus.WAITING_SETTLEMENT}) {
            gatewayReports(s);
            assertThat(adapter.refundability("PAY-1")).as("%s", s)
                    .isEqualTo(Refundability.REFUNDABLE);
        }
    }

    @Test
    void notRefundableWhenFailedRejectedOrUnpaid() {
        for (PaymentStatus s : new PaymentStatus[]{
                PaymentStatus.FAILED, PaymentStatus.REJECTED, PaymentStatus.CREATED, PaymentStatus.INITIATED}) {
            gatewayReports(s);
            assertThat(adapter.refundability("PAY-1")).as("%s", s)
                    .isEqualTo(Refundability.NOT_REFUNDABLE);
        }
    }
}
