package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@Testcontainers
@Transactional
class BookingCancellationControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired WebApplicationContext context;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired WorkshopService workshops;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;

    @MockitoBean PaymentGateway gateway;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    private Order paidOrder(String key, boolean confirm) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Product ws = workshops.createWorkshop("WS " + key, "ws-" + key, "l", 27);
        workshops.addSession(ws, now.plusDays(7), 5, 15_000L, "WS-" + key);
        String token = cart.addItem(null, "WS-" + key, 1).token();
        Order order = checkout.placeOrder(token, new PlaceOrderCommand(key, "T",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "gls"));
        payments.save(Payment.initiate(order, "PAY-" + key, order.getTotalGrossHuf()));
        Payment p = payments.findFirstByOrderOrderByIdDesc(order).orElseThrow();
        if (confirm) {
            p.markState(Payment.State.CONFIRMED, "ok");
            order.transitionTo(OrderStatus.PAID);
        }
        return orders.save(order);
    }

    @Test
    void cancelReturns200WithResult() throws Exception {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));
        Order order = paidOrder("c200", true);
        Long lineId = order.getItems().get(0).getId();

        mvc.perform(post("/api/admin/order-items/{id}/cancel", lineId)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderItemId").value(lineId))
                .andExpect(jsonPath("$.refundedHuf").value(15_000));
    }

    @Test
    void cancelOnUnpaidOrderReturns409() throws Exception {
        Order order = paidOrder("c409", false);
        Long lineId = order.getItems().get(0).getId();

        mvc.perform(post("/api/admin/order-items/{id}/cancel", lineId)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("PAID/PACKING")));
    }

    @Test
    void cancelUnknownItemReturns404() throws Exception {
        mvc.perform(post("/api/admin/order-items/{id}/cancel", 999999L)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelWithoutAdminRoleReturns403() throws Exception {
        mvc.perform(post("/api/admin/order-items/{id}/cancel", 12345L)
                        .with(user("user").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelReturns502WhenGatewayDeclines() throws Exception {
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(false, "declined"));
        Order order = paidOrder("c502", true);
        Long lineId = order.getItems().get(0).getId();

        mvc.perform(post("/api/admin/order-items/{id}/cancel", lineId)
                        .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isBadGateway());
    }
}
