package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Payment;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@Testcontainers
@Transactional
class OrderAdminControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    WebApplicationContext context;

    @Autowired
    CartService cart;

    @Autowired
    CheckoutService checkout;

    @Autowired
    CatalogImporter importer;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @MockitoBean
    PaymentGateway gateway;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        when(gateway.refundability(anyString())).thenReturn(PaymentGateway.Refundability.REFUNDABLE);
        when(gateway.refund(anyString(), anyLong())).thenReturn(new PaymentGateway.RefundResult(true, "ok"));
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint), List.of()));
        String token = cart.addItem(null, "FES-1", 1).token();
        checkout.placeOrder(token, new PlaceOrderCommand("oc-1", "Kovács Béla", "bela@example.com", null,
                "1111", "Budapest", "Fő u. 1.", null, "pickup"));
    }

    @Test
    void listReturnsOrdersWithTotalCountHeaderForAdmin() throws Exception {
        mvc.perform(get("/api/admin/orders").with(user("a@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kovács Béla")));
    }

    @Test
    void listRejectsNonAdmin() throws Exception {
        mvc.perform(get("/api/admin/orders").with(user("c@example.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void detailReturnsOrderForAdmin() throws Exception {
        Long id = orderRepository.findAll().getFirst().getId();
        mvc.perform(get("/api/admin/orders/{id}", id).with(user("a@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Kovács Béla")))
                .andExpect(content().string(containsString("FES-1")));
    }

    @Test
    void detailReturns404ForUnknownId() throws Exception {
        mvc.perform(get("/api/admin/orders/999999").with(user("a@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void transitionAdvancesStatusForAdmin() throws Exception {
        // the @BeforeEach seeds one NEW order; drive it to PAID first
        Order order = orderRepository.findAll().getFirst();
        order.transitionTo(OrderStatus.PAID);
        orderRepository.save(order);

        mvc.perform(post("/api/admin/orders/" + order.getId() + "/transition")
                        .with(user("a@example.com").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"PACKING\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PACKING")));

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PACKING);
    }

    @Test
    void illegalTransitionReturns409() throws Exception {
        Order order = orderRepository.findAll().getFirst(); // still NEW
        mvc.perform(post("/api/admin/orders/" + order.getId() + "/transition")
                        .with(user("a@example.com").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void transitionRejectsNonAdmin() throws Exception {
        Order order = orderRepository.findAll().getFirst();
        mvc.perform(post("/api/admin/orders/" + order.getId() + "/transition")
                        .with(user("c@example.com").roles("CUSTOMER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"PACKING\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMissingStatusWithBadRequest() throws Exception {
        Long id = orderRepository.findAll().getFirst().getId();
        mvc.perform(post("/api/admin/orders/" + id + "/transition")
                        .with(user("a@example.com").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownStatusWithBadRequest() throws Exception {
        Long id = orderRepository.findAll().getFirst().getId();
        mvc.perform(post("/api/admin/orders/" + id + "/transition")
                        .with(user("a@example.com").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("{\"status\":\"BOGUS\"}"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("a@example.com").roles("ADMIN");
    }

    @Test
    void refundEndpointRefundsPaidOrder() throws Exception {
        Order order = orderRepository.findAll().getFirst();
        // give it a confirmed payment and drive to PAID
        paymentRepository.save(Payment.initiate(order, "PAY-REF", order.getTotalGrossHuf()));
        var p = paymentRepository.findFirstByOrderOrderByIdDesc(order).orElseThrow();
        p.markState(Payment.State.CONFIRMED, "ok");
        order.transitionTo(OrderStatus.PAID);
        orderRepository.save(order);

        mvc.perform(post("/api/admin/orders/" + order.getId() + "/refund")
                        .with(admin()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("REFUNDED")));
    }

    @Test
    void refundEndpointRejectsNewOrderWith409() throws Exception {
        Order order = orderRepository.findAll().getFirst(); // NEW, unpaid
        mvc.perform(post("/api/admin/orders/" + order.getId() + "/refund")
                        .with(admin()).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void refundEndpointReturns502WhenGatewayDeclines() throws Exception {
        when(gateway.refund(anyString(), anyLong()))
                .thenReturn(new PaymentGateway.RefundResult(false, "bank declined"));
        Order order = orderRepository.findAll().getFirst();
        paymentRepository.save(Payment.initiate(order, "PAY-502", order.getTotalGrossHuf()));
        var p = paymentRepository.findFirstByOrderOrderByIdDesc(order).orElseThrow();
        p.markState(Payment.State.CONFIRMED, "ok");
        order.transitionTo(OrderStatus.PAID);
        orderRepository.save(order);

        mvc.perform(post("/api/admin/orders/" + order.getId() + "/refund")
                        .with(admin()).with(csrf()))
                .andExpect(status().isBadGateway());

        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }
}
