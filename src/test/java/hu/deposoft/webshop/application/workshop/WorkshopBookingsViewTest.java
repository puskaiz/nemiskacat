package hu.deposoft.webshop.application.workshop;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.domain.order.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class WorkshopBookingsViewTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired WorkshopService workshops;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired OrderRepository orders;

    @Test
    void bookingViewExposesLineIdGrossAndCancelledSeats() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Product ws = workshops.createWorkshop("WS", "ws-v", "l", 27);
        workshops.addSession(ws, now.plusDays(7), 5, 15_000L, "WS-V");
        String token = cart.addItem(null, "WS-V", 2).token();
        Order order = checkout.placeOrder(token, new PlaceOrderCommand("v", "T",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "gls"));
        OrderItem line = order.getItems().get(0);
        line.cancelWholeLine();
        orders.save(order);

        var view = workshops.bookings(ws.getId()).get(0);
        assertThat(view.orderItemId()).isEqualTo(line.getId());
        assertThat(view.seats()).isEqualTo(2);
        assertThat(view.cancelledSeats()).isEqualTo(2);
        assertThat(view.lineGrossHuf()).isEqualTo(line.getLineGrossHuf());
        assertThat(view.unitGrossHuf()).isEqualTo(line.getUnitGrossHuf());
    }
}
