package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import jakarta.servlet.http.Cookie;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** T9 web flow: /penztar form -> order placement -> confirmation page (WP slug preserved). */
@SpringBootTest
@Testcontainers
@Transactional
class CheckoutPagesTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    WebApplicationContext context;

    @Autowired
    CatalogImporter importer;

    @Autowired
    CartService cartService;

    MockMvc mvc;
    String cartToken;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        SourceProduct product = new SourceProduct(100L, "festek", "Pénztár Teszt Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(),
                List.of(product), List.of()));
        cartToken = cartService.addItem(null, "FES-1", 2).token();
    }

    @Test
    void checkoutPageShowsItemsAndShippingFees() throws Exception {
        mvc.perform(get("/penztar").cookie(new Cookie("nk_cart", cartToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("Pénztár Teszt Festék")))
                .andExpect(content().string(containsString("GLS futárszolgálat")))
                .andExpect(content().string(containsString("850")));
    }

    @Test
    void checkoutWithEmptyCartRedirectsToCart() throws Exception {
        mvc.perform(get("/penztar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kosar"));
    }

    @Test
    void placingOrderRedirectsToConfirmationWhichShowsTheOrder() throws Exception {
        mvc.perform(post("/penztar").with(csrf())
                        .cookie(new Cookie("nk_cart", cartToken))
                        .param("clientKey", "web-key-1")
                        .param("customerName", "Teszt Elek")
                        .param("email", "teszt@example.com")
                        .param("phone", "+36201234567")
                        .param("postcode", "1111")
                        .param("city", "Budapest")
                        .param("addressLine", "Fő utca 1.")
                        .param("shippingMethodCode", "gls"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/penztar/koszonjuk/web-key-1"));

        mvc.perform(get("/penztar/koszonjuk/web-key-1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pénztár Teszt Festék")))
                .andExpect(content().string(containsString("NK-")))
                .andExpect(content().string(containsString("GLS futárszolgálat")));
    }

    @Test
    void checkoutWithUnavailableItemRedirectsToCartWithMessage() throws Exception {
        // Product with only 1 in stock, but the cart asks for 2 -> not fulfillable
        // at checkout. The user must be told why, not bounced silently.
        SourceProduct scarce = new SourceProduct(101L, "keves", "Kevés Készlet", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-SCARCE", 3700L, null, null, null, true, 1, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(),
                List.of(scarce), List.of()));
        String scarceToken = cartService.addItem(null, "FES-SCARCE", 2).token();

        mvc.perform(get("/penztar").cookie(new Cookie("nk_cart", scarceToken)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kosar"))
                // the notice must NAME the offending product AND state how many remain
                // (stock 1, wanted 2 -> "még 1 elérhető"); the sku->remaining map is
                // flashed so the cart row badge can show the limit
                .andExpect(flash().attribute("unavailableMessage", containsString("Kevés Készlet")))
                .andExpect(flash().attribute("unavailableMessage", containsString("még 1 elérhető")))
                .andExpect(flash().attributeExists("unavailableRemaining"));
    }

    @Test
    void unknownConfirmationKeyIs404() throws Exception {
        mvc.perform(get("/penztar/koszonjuk/no-such-key"))
                .andExpect(status().isNotFound());
    }
}
