package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.AuditEntryRepository;
import hu.deposoft.webshop.domain.order.InvoiceRepository;
import hu.deposoft.webshop.domain.order.InvoiceType;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@Transactional
class BookingCancellationServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired BookingCancellationService bookings;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired InvoiceRepository invoices;
    @Autowired AuditEntryRepository audit;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired WorkshopService workshops;

    @MockitoBean PaymentGateway gateway;

    /** A paid order: two workshop lines, CONFIRMED payment, driven to status. */
    private Order paidWorkshopOrder(String key, OrderStatus status) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Product ws = workshops.createWorkshop("WS " + key, "ws-" + key, "leiras", 27);
        workshops.addSession(ws, now.plusDays(7), 5, 15_000L, "WSA-" + key);
        workshops.addSession(ws, now.plusDays(8), 5, 12_000L, "WSB-" + key);
        String token = cart.addItem(null, "WSA-" + key, 1).token();
        cart.addItem(token, "WSB-" + key, 1);
        Order order = checkout.placeOrder(token, new PlaceOrderCommand(key, "Teszt",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "gls"));
        payments.save(Payment.initiate(order, "PAY-" + key, order.getTotalGrossHuf()));
        Payment p = payments.findFirstByOrderOrderByIdDesc(order).orElseThrow();
        p.markState(Payment.State.CONFIRMED, "ok");
        order.transitionTo(OrderStatus.PAID);
        if (status == OrderStatus.PACKING) order.transitionTo(OrderStatus.PACKING);
        return orders.save(order);
    }

    private OrderItem firstLine(Order order) {
        return order.getItems().get(0);
    }

    @Test
    void cancelsLinePartialRefundFreesSeatKeepsOrder() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));
        Order order = paidWorkshopOrder("ok", OrderStatus.PAID);
        OrderItem line = firstLine(order);
        long lineGross = line.getLineGrossHuf();

        long refunded = bookings.cancelBooking(line.getId());
        assertThat(refunded).isEqualTo(lineGross);

        Order reloaded = orders.findById(order.getId()).orElseThrow();
        OrderItem cancelled = reloaded.getItems().stream().filter(i -> i.getId().equals(line.getId())).findFirst().orElseThrow();
        OrderItem other = reloaded.getItems().stream().filter(i -> !i.getId().equals(line.getId())).findFirst().orElseThrow();

        verify(gateway, times(1)).refund(eq("PAY-ok"), eq(lineGross));
        assertThat(cancelled.getCancelledQuantity()).isEqualTo(cancelled.getQuantity());
        assertThat(other.getCancelledQuantity()).isZero();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payments.findFirstByOrderOrderByIdDesc(reloaded).orElseThrow().getState())
                .isEqualTo(Payment.State.CONFIRMED);
        assertThat(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("order_item", String.valueOf(line.getId())))
                .anySatisfy(e -> assertThat(e.getAction()).isEqualTo("BOOKING_CANCELLED"));
        assertThat(invoices.findByOrderItemAndType(cancelled, InvoiceType.CREDIT_NOTE)).isPresent();
    }

    @Test
    void rejectsCancelOnNewOrder() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Product ws = workshops.createWorkshop("WS new2", "ws-new2", "l", 27);
        workshops.addSession(ws, now.plusDays(7), 5, 15_000L, "WSN");
        String token = cart.addItem(null, "WSN", 1).token();
        Order fresh = checkout.placeOrder(token, new PlaceOrderCommand("new2", "T",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "gls"));
        Long lineId = fresh.getItems().get(0).getId();

        assertThatThrownBy(() -> bookings.cancelBooking(lineId))
                .isInstanceOf(BookingCancellationService.BookingCancelNotAllowedException.class);
        verify(gateway, never()).refund(anyString(), anyLong());
    }

    @Test
    void gatewayFailureRollsBack() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(false, "declined"));
        Order order = paidWorkshopOrder("fail", OrderStatus.PAID);
        OrderItem line = firstLine(order);

        assertThatThrownBy(() -> bookings.cancelBooking(line.getId()))
                .isInstanceOf(BookingCancellationService.BookingRefundFailedException.class);

        assertThat(orders.findById(order.getId()).orElseThrow().getItems().stream()
                .filter(i -> i.getId().equals(line.getId())).findFirst().orElseThrow()
                .getCancelledQuantity()).isZero();
    }

    @Test
    void cancelIsIdempotent() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));
        Order order = paidWorkshopOrder("idem", OrderStatus.PAID);
        OrderItem line = firstLine(order);

        bookings.cancelBooking(line.getId());
        bookings.cancelBooking(line.getId());

        verify(gateway, times(1)).refund(anyString(), anyLong());
    }

    @Test
    void twoLineCancelsProduceTwoCreditNoteRows() {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));
        Order order = paidWorkshopOrder("two", OrderStatus.PAID);
        OrderItem a = order.getItems().get(0);
        OrderItem b = order.getItems().get(1);

        bookings.cancelBooking(a.getId());
        bookings.cancelBooking(b.getId());

        assertThat(invoices.findByOrderItemAndType(a, InvoiceType.CREDIT_NOTE)).isPresent();
        assertThat(invoices.findByOrderItemAndType(b, InvoiceType.CREDIT_NOTE)).isPresent();
        assertThat(invoices.findAll().stream()
                .filter(i -> i.getType() == InvoiceType.CREDIT_NOTE && i.getOrderItem() != null)
                .count()).isEqualTo(2);
    }
}
