package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.customer.CustomerImporter;
import hu.deposoft.webshop.integrations.wordpress.SourceCustomer;
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

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Admin customer list (read-only). ADMIN-gated by SecurityConfig: 401 anonymous,
 * 403 non-admin, paginated + searchable for ADMIN, newest-first.
 */
@SpringBootTest
@Testcontainers
@Transactional
class CustomerAdminControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    private static final String WP_HASH = "$P$BDxkiVZNDAZCUYJPWfhZ/YRxoK5khY1";

    @Autowired
    WebApplicationContext context;

    @Autowired
    CustomerImporter customerImporter;

    MockMvc mvc;

    @BeforeEach
    void setUp() throws InterruptedException {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        // Seed in separate runs with a gap so createdAt is strictly increasing => deterministic DESC order.
        customerImporter.run(List.of(
                new SourceCustomer(800L, "anna", "anna@example.com", WP_HASH, "Anna", "Kovács", "Anna Kovács", "customer")));
        Thread.sleep(5);
        customerImporter.run(List.of(
                new SourceCustomer(801L, "bela", "bela@shop.com", WP_HASH, "Béla", "Nagy", "Béla Nagy", "subscriber")));
        Thread.sleep(5);
        customerImporter.run(List.of(
                new SourceCustomer(802L, "cili", "cili@example.com", WP_HASH, "Cili", "Tóth", "Cili Tóth", "administrator")));
    }

    @Test
    void listReturnsCustomersNewestFirstWithTotalCountForAdmin() throws Exception {
        mvc.perform(get("/api/admin/customers").with(user("a@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "3"))
                // newest seeded (Cili) must come first
                .andExpect(jsonPath("$[0].email").value("cili@example.com"))
                .andExpect(jsonPath("$[0].name").value("Cili Tóth"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].enabled").value(true))
                .andExpect(jsonPath("$[2].email").value("anna@example.com"));
    }

    @Test
    void filtersByEmailSubstring() throws Exception {
        mvc.perform(get("/api/admin/customers").param("q", "@shop.com")
                        .with(user("a@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(content().string(containsString("bela@shop.com")))
                .andExpect(content().string(not(containsString("anna@example.com"))));
    }

    @Test
    void filtersByNameSubstring() throws Exception {
        mvc.perform(get("/api/admin/customers").param("q", "tóth")
                        .with(user("a@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].email").value("cili@example.com"));
    }

    @Test
    void rejectsAnonymousWith401() throws Exception {
        mvc.perform(get("/api/admin/customers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsNonAdminWith403() throws Exception {
        mvc.perform(get("/api/admin/customers").with(user("c@example.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }
}
