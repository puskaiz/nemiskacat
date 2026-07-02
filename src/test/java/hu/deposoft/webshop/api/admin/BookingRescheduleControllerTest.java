package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.workshop.WorkshopSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@Testcontainers
@Transactional
class BookingRescheduleControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired WebApplicationContext context;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired WorkshopService workshops;
    @Autowired WorkshopSessionRepository sessions;

    MockMvc mvc;
    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    private Order bookA(String key, long priceB) {
        Product ws = workshops.createWorkshop("WS " + key, "ws-" + key, "l", 27);
        workshops.addSession(ws, NOW.plusDays(7), 5, 15_000L, "WSA-" + key);
        workshops.addSession(ws, NOW.plusDays(8), 5, priceB, "WSB-" + key);
        String token = cart.addItem(null, "WSA-" + key, 1).token();
        return checkout.placeOrder(token, new PlaceOrderCommand(key, "T", "t@example.com", null,
                "1111", "Budapest", "Fő u. 1.", null, "gls"));
    }

    private Long sessionIdBySku(String sku) {
        return sessions.findAll().stream().filter(s -> sku.equals(s.getVariant().getSku()))
                .findFirst().orElseThrow().getId();
    }

    @Test
    void rescheduleReturns200() throws Exception {
        Order order = bookA("r2", 15_000L);
        Long lineId = order.getItems().get(0).getId();
        Long target = sessionIdBySku("WSB-r2");

        mvc.perform(post("/api/admin/order-items/{id}/reschedule", lineId)
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("{\"targetSessionId\":" + target + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderItemId").value(lineId))
                .andExpect(jsonPath("$.sessionId").value(target));
    }

    @Test
    void differentPriceReturns409() throws Exception {
        Order order = bookA("r409", 18_000L);
        Long lineId = order.getItems().get(0).getId();
        Long target = sessionIdBySku("WSB-r409");

        mvc.perform(post("/api/admin/order-items/{id}/reschedule", lineId)
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("{\"targetSessionId\":" + target + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownSessionReturns404() throws Exception {
        Order order = bookA("r404", 15_000L);
        Long lineId = order.getItems().get(0).getId();

        mvc.perform(post("/api/admin/order-items/{id}/reschedule", lineId)
                        .with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType("application/json")
                        .content("{\"targetSessionId\":999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void nonAdminReturns403() throws Exception {
        mvc.perform(post("/api/admin/order-items/{id}/reschedule", 12345L)
                        .with(user("user").roles("USER")).with(csrf())
                        .contentType("application/json")
                        .content("{\"targetSessionId\":1}"))
                .andExpect(status().isForbidden());
    }
}
