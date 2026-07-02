package hu.deposoft.webshop.api;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * B1: GET /api/cart — read-only cart endpoint for the drawer.
 * Verifies no-store header, empty-cart shape, and populated-cart shape.
 */
@SpringBootTest
@Testcontainers
@Transactional
class CartControllerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    WebApplicationContext context;

    @Autowired
    CatalogImporter importer;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
        SourceProduct product = new SourceProduct(200L, "festek-b1", "B1 Teszt Festék", "simple", "publish",
                null, null, null, null, null, List.of(20L), List.of(),
                "FES-B1", 4200L, null, null, null, true, 5, null, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(20L, "kat2", "Kat2", null, 0, null, null, null)),
                List.<SourceAttribute>of(),
                List.of(product), List.of()));
    }

    @Test
    void getCart_emptyCart_returnsZeroAndNoStore() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void getCart_populatedCart_returnsItemsAndCount() throws Exception {
        String token = addItemAndExtractToken();

        mockMvc.perform(get("/api/cart").cookie(new Cookie(CartController.CART_COOKIE, token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.items[0].name").value("B1 Teszt Festék"));
    }

    private String addItemAndExtractToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cart/items").with(csrf())
                        .contentType("application/json")
                        .content("{\"sku\":\"FES-B1\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andReturn();
        jakarta.servlet.http.Cookie cookie = result.getResponse().getCookie(CartController.CART_COOKIE);
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }
}
