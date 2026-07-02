package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.customer.CustomerImporter;
import hu.deposoft.webshop.domain.cart.CartRepository;
import hu.deposoft.webshop.domain.customer.Customer;
import hu.deposoft.webshop.domain.customer.CustomerRepository;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import hu.deposoft.webshop.integrations.wordpress.SourceCustomer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * T13 acceptance: migrated WooCommerce customer logs in against the legacy phpass
 * hash, which is then upgraded to bcrypt (upgrade-on-login); registration creates
 * a bcrypt account; the guest cart merges into the account on login.
 */
@SpringBootTest
@Testcontainers
@Transactional
class CustomerAccountTest {

    // Real phpass vector from the WP container: password "Secret123".
    private static final String WP_HASH = "$P$BDxkiVZNDAZCUYJPWfhZ/YRxoK5khY1";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    WebApplicationContext context;

    @Autowired
    CustomerImporter customerImporter;

    @Autowired
    CustomerRepository customers;

    @Autowired
    CatalogImporter catalogImporter;

    @Autowired
    CartService cartService;

    @Autowired
    CartRepository carts;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    hu.deposoft.webshop.application.customer.CustomerUserDetailsService userDetails;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        customerImporter.run(List.of(new SourceCustomer(
                3768L, "agi", "vasarlo@example.com", WP_HASH, "Ágnes", "Nagy", "Nagy Ágnes", "customer")));
    }

    @Test
    void migratedCustomerHashIsStoredWithWpPrefix() {
        Customer c = customers.findByWpUserId(3768L).orElseThrow();
        assertThat(c.getPasswordHash()).isEqualTo("{wp}" + WP_HASH);
    }

    @Test
    void loginWithLegacyPasswordSucceedsAndUpgradesToBcrypt() throws Exception {
        mvc.perform(formLogin("/fiokom/belepes").user("email", "vasarlo@example.com").password("Secret123"))
                .andExpect(authenticated());

        // upgrade-on-login: the stored hash is now bcrypt and still verifies
        Customer c = customers.findByWpUserId(3768L).orElseThrow();
        assertThat(c.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches("Secret123", c.getPasswordHash())).isTrue();
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        mvc.perform(formLogin("/fiokom/belepes").user("email", "vasarlo@example.com").password("nope"))
                .andExpect(unauthenticated());
    }

    @Test
    void caseInsensitiveEmailLogin() throws Exception {
        mvc.perform(formLogin("/fiokom/belepes").user("email", "VASARLO@Example.com").password("Secret123"))
                .andExpect(authenticated());
    }

    @Test
    void loginWithWordPressUsernameSucceeds() throws Exception {
        mvc.perform(formLogin("/fiokom/belepes").user("email", "agi").password("Secret123"))
                .andExpect(authenticated());
    }

    @Test
    void migratedRoleBecomesGrantedAuthority() {
        customerImporter.run(List.of(new SourceCustomer(
                14L, "fonok", "admin@example.com", WP_HASH, "Fő", "Nök", "Főnök", "administrator")));

        var details = userDetails.loadUserByUsername("admin@example.com");
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        // the customer (default fixture) is ROLE_CUSTOMER
        assertThat(userDetails.loadUserByUsername("vasarlo@example.com").getAuthorities())
                .extracting(Object::toString).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void registrationCreatesBcryptAccount() throws Exception {
        mvc.perform(post("/fiokom/regisztracio").with(csrf())
                        .param("email", "uj@example.com").param("password", "hosszuJelszo1")
                        .param("firstName", "Teszt").param("lastName", "Elek"))
                .andExpect(status().is3xxRedirection());

        Customer c = customers.findByEmailIgnoreCase("uj@example.com").orElseThrow();
        assertThat(c.getPasswordHash()).startsWith("{bcrypt}");
        assertThat(c.getWpUserId()).isNull();
    }

    @Test
    void accountPageShowsLoginWhenAnonymousAndProfileWhenAuthenticated() throws Exception {
        mvc.perform(get("/fiokom"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Belépés")));

        mvc.perform(get("/fiokom").with(user("vasarlo@example.com")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rendeléseim")));
    }

    @Test
    void guestCartMergesIntoAccountOnLogin() throws Exception {
        // seed a product and a guest cart
        catalogImporter.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(),
                List.of(new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                        null, null, null, null, null, List.of(10L), List.of(),
                        "FES-1", 3700L, null, null, null, true, 10, null, List.of(), List.of(), List.of())), List.of()));
        String guestToken = cartService.addItem(null, "FES-1", 2).token();

        MvcResult result = mvc.perform(post("/fiokom/belepes").with(csrf())
                        .param("email", "vasarlo@example.com").param("password", "Secret123")
                        .cookie(new Cookie("nk_cart", guestToken)))
                .andExpect(authenticated())
                .andReturn();

        Cookie merged = result.getResponse().getCookie("nk_cart");
        String token = merged != null ? merged.getValue() : guestToken;
        long customerId = customers.findByWpUserId(3768L).orElseThrow().getId();
        assertThat(carts.findByUserId(customerId)).isPresent();
        assertThat(cartService.view(token).count()).isEqualTo(2);
    }
}
