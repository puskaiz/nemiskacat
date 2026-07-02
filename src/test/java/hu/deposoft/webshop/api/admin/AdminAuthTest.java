package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.customer.CustomerImporter;
import hu.deposoft.webshop.integrations.wordpress.SourceCustomer;
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

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Admin auth (T15): only ADMIN-role accounts may use /api/admin; the SPA logs in
 * with email+password against the existing customer credentials and gets a
 * session cookie.
 *
 * <p>The seed password for both accounts is "Secret123". The credential below is
 * the real WordPress phpass hash of "Secret123" (the same vector CustomerAccountTest
 * uses): CustomerImporter stores every imported hash with the {@code {wp}} delegating
 * prefix, so the value must be a {wp}-compatible hash for the AuthenticationManager
 * to verify it. A bare bcrypt hash would be stored as {@code {wp}{bcrypt}...} and
 * fail to match.
 */
@SpringBootTest
@Testcontainers
class AdminAuthTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    // WordPress phpass hash of "Secret123" (stored as {wp}... by CustomerImporter).
    private static final String WP_HASH = "$P$BDxkiVZNDAZCUYJPWfhZ/YRxoK5khY1";

    @Autowired
    WebApplicationContext context;

    @Autowired
    CustomerImporter customerImporter;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        // an ADMIN and a plain CUSTOMER, both with password "Secret123"
        customerImporter.run(List.of(
                new SourceCustomer(900L, "boss", "boss@example.com", WP_HASH, "Fő", "Nök", "Fő Nök", "administrator"),
                new SourceCustomer(901L, "vevo", "vevo@example.com", WP_HASH, "Vevő", "Ő", "Vevő Ő", "customer")));
    }

    @Test
    void adminEndpointRejectsAnonymous() throws Exception {
        mvc.perform(get("/api/admin/workshops"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointRejectsNonAdminRole() throws Exception {
        mvc.perform(get("/api/admin/workshops").with(user("vevo@example.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointAllowsAdminRole() throws Exception {
        mvc.perform(get("/api/admin/workshops").with(user("boss@example.com").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void loginRejectsNonAdmin() throws Exception {
        mvc.perform(post("/api/admin/auth/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"vevo@example.com\",\"password\":\"Secret123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginAcceptsAdminAndMeReturnsIt() throws Exception {
        var session = mvc.perform(post("/api/admin/auth/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"boss@example.com\",\"password\":\"Secret123\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("boss@example.com")))
                .andReturn().getRequest().getSession();

        mvc.perform(get("/api/admin/auth/me").session(
                        (org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ADMIN")));
    }

    @Test
    void loginRotatesSessionIdAgainstFixation() throws Exception {
        // Pre-existing (anonymous) session the attacker might have fixated.
        var preLogin = new org.springframework.mock.web.MockHttpSession();
        String preLoginId = preLogin.getId();

        var postLogin = (org.springframework.mock.web.MockHttpSession) mvc.perform(
                        post("/api/admin/auth/login").with(csrf())
                                .session(preLogin)
                                .contentType("application/json")
                                .content("{\"email\":\"boss@example.com\",\"password\":\"Secret123\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession();

        // changeSessionId() rotated the id on authentication, so the fixated id is no longer valid.
        org.junit.jupiter.api.Assertions.assertNotEquals(preLoginId, postLogin.getId(),
                "session id must rotate on login (anti session-fixation)");

        // The authenticated context lives on the rotated session and is reachable.
        mvc.perform(get("/api/admin/auth/me").session(postLogin))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ADMIN")));
    }

    @Test
    void loginRejectsBadPassword() throws Exception {
        mvc.perform(post("/api/admin/auth/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"boss@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
}
