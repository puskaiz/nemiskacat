package hu.deposoft.webshop.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * The bank posts its signed return to /khpos/return with no CSRF token. CSRF must
 * not block it (the starter verifies the bank signature instead) — otherwise the
 * callback never runs, the customer hits a 403, and the order is only rescued
 * later by the pending-payment re-check. Other POSTs must stay CSRF-protected.
 */
@SpringBootTest
@Testcontainers
class PaymentReturnCsrfTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void bankReturnPostIsNotBlockedByCsrf() throws Exception {
        // No CSRF token, as the bank sends. Must pass CSRF and reach dispatch; the
        // KHPos controller is absent in the test profile (khpos.enabled=false), so
        // the request lands on "no handler" (404) rather than being rejected (403).
        mvc.perform(post("/khpos/return"))
                .andExpect(status().isNotFound());
    }

    @Test
    void otherPostsStillRequireCsrf() throws Exception {
        mvc.perform(post("/fiokom/regisztracio"))
                .andExpect(status().isForbidden());
    }
}
