package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductImage;
import hu.deposoft.webshop.domain.catalog.ProductImageRepository;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.order.AuditEntryRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@Testcontainers
@Transactional
class WorkshopAdminControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    WebApplicationContext context;

    @Autowired
    WorkshopService workshops;

    @Autowired
    AuditEntryRepository audit;

    @Autowired
    CartService cart;

    @Autowired
    CheckoutService checkout;

    @Autowired
    ProductImageRepository images;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return user("boss@example.com").roles("ADMIN");
    }

    @Test
    void createsWorkshopAndAuditsIt() throws Exception {
        mvc.perform(post("/api/admin/workshops").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Bútorfestés\",\"slug\":\"butor-ws\",\"description\":\"<p>x</p>\",\"vatRatePercent\":27}"))
                .andExpect(status().isCreated())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("butor-ws")));

        assertThat(audit.findAll()).anySatisfy(e -> {
            assertThat(e.getAction()).isEqualTo("WORKSHOP_CREATE");
            assertThat(e.getActor()).isEqualTo("boss@example.com");
        });
    }

    @Test
    void listsWorkshopsWithTotalCountHeader() throws Exception {
        workshops.createWorkshop("A", "ws-list-a", "x", 27);

        mvc.perform(get("/api/admin/workshops").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"));
    }

    @Test
    void addsASessionToAWorkshop() throws Exception {
        Product ws = workshops.createWorkshop("WS", "ws-sess", "x", 27);

        mvc.perform(post("/api/admin/workshops/" + ws.getId() + "/sessions").with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"startAt\":\"" + OffsetDateTime.now(ZoneOffset.UTC).plusDays(7)
                                + "\",\"capacity\":8,\"priceHuf\":15000,\"sku\":\"WS-SESS-1\"}"))
                .andExpect(status().isCreated());

        assertThat(workshops.listSessions(ws.getId())).hasSize(1);
    }

    @Test
    void cancelsAnUnbookedSession() throws Exception {
        Product ws = workshops.createWorkshop("WS", "ws-del", "x", 27);
        Variant seat = workshops.addSession(ws, OffsetDateTime.now(ZoneOffset.UTC).plusDays(7), 5, 15000L, "WS-DEL-1");

        // The cancel endpoint takes the session id, not the variant id
        Long sessionId = workshops.listSessions(ws.getId()).getFirst().getId();

        mvc.perform(delete("/api/admin/sessions/" + sessionId).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(workshops.listSessions(ws.getId())).isEmpty();
    }

    @Test
    void updatesASession() throws Exception {
        Product ws = workshops.createWorkshop("WS", "ws-upd", "x", 27);
        workshops.addSession(ws, OffsetDateTime.now(ZoneOffset.UTC).plusDays(7), 5, 15000L, "WS-UPD-1");
        Long sessionId = workshops.listSessions(ws.getId()).getFirst().getId();

        OffsetDateTime newStart = OffsetDateTime.now(ZoneOffset.UTC).plusDays(14);
        mvc.perform(put("/api/admin/sessions/" + sessionId).with(admin()).with(csrf())
                        .contentType("application/json")
                        .content("{\"startAt\":\"" + newStart + "\",\"capacity\":12,\"priceHuf\":20000,\"sku\":\"WS-UPD-2\"}"))
                .andExpect(status().isOk());

        var updated = workshops.listSessions(ws.getId()).getFirst();
        assertThat(updated.getCapacity()).isEqualTo(12);
        assertThat(updated.getVariant().getSku()).isEqualTo("WS-UPD-2");
    }

    @Test
    void bookingsEndpointListsAttendees() throws Exception {
        Product ws = workshops.createWorkshop("WS", "ws-bk", "x", 27);
        workshops.addSession(ws, OffsetDateTime.now(ZoneOffset.UTC).plusDays(7), 8, 15000L, "WS-BK-EP");
        String token = cart.addItem(null, "WS-BK-EP", 2).token();
        checkout.placeOrder(token, new PlaceOrderCommand("ws-bk-ep", "Kovács Béla",
                "bela@example.com", "+36201234567", "1111", "Budapest", "Fő u. 1.", null, "pickup"));

        mvc.perform(get("/api/admin/workshops/" + ws.getId() + "/bookings").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kovács Béla")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("+36201234567")));
    }

    @Test
    void getExposesStatusSessionCountAndImages() throws Exception {
        Product ws = workshops.createWorkshop("Nézet", "ws-view", "x", 27);
        workshops.addSession(ws, OffsetDateTime.now(ZoneOffset.UTC).plusDays(7), 5, 15000L, "WS-VIEW-1");
        images.save(ProductImage.create(ws, "wp/cover.jpg", "borító", 0, true));

        mvc.perform(get("/api/admin/workshops/" + ws.getId()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"PUBLISHED\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"sessionCount\":1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"featured\":true")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("borító")));
    }

    @Test
    void listExposesStatusSessionCountAndImages() throws Exception {
        Product ws = workshops.createWorkshop("Lista", "ws-view-list", "x", 27);
        images.save(ProductImage.create(ws, "wp/c.jpg", "kép", 0, true));

        mvc.perform(get("/api/admin/workshops").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"sessionCount\":")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"images\":")));
    }

    @Test
    void deletesAnUnbookedWorkshop() throws Exception {
        Product ws = workshops.createWorkshop("Törlés", "ws-del-ep", "x", 27);
        workshops.addSession(ws, OffsetDateTime.now(ZoneOffset.UTC).plusDays(7), 5, 15000L, "WS-DEL-EP");

        mvc.perform(delete("/api/admin/workshops/" + ws.getId()).with(admin()).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(audit.findAll()).anySatisfy(e -> assertThat(e.getAction()).isEqualTo("WORKSHOP_DELETE"));
    }

    @Test
    void deleteOfBookedWorkshopReturns409() throws Exception {
        Product ws = workshops.createWorkshop("Foglalt", "ws-del-ep-bk", "x", 27);
        workshops.addSession(ws, OffsetDateTime.now(ZoneOffset.UTC).plusDays(7), 5, 15000L, "WS-DEL-EP-BK");
        String token = cart.addItem(null, "WS-DEL-EP-BK", 1).token();
        checkout.placeOrder(token, new PlaceOrderCommand("ws-del-ep-bk", "Teszt", "t@example.com", null,
                "1111", "Budapest", "Fő u. 1.", null, "pickup"));

        mvc.perform(delete("/api/admin/workshops/" + ws.getId()).with(admin()).with(csrf()))
                .andExpect(status().isConflict());
    }
}
