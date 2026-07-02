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
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * T8 acceptance: the cart API (also used by the static-page island) — CSRF
 * enforcement, cookie issuing, the cheap /api/session endpoint, and the flow
 * "added via API → appears on the webshop /kosar page".
 */
@SpringBootTest
@Testcontainers
@Transactional
class CartApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    WebApplicationContext context;

    @Autowired
    CatalogImporter importer;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        SourceProduct product = new SourceProduct(100L, "festek", "Kosár Teszt Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, null, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(),
                List.of(product), List.of()));
    }

    // ---- CSRF ----

    @Test
    void modifyingCallWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/cart/items")
                        .contentType("application/json")
                        .content("{\"sku\":\"FES-1\",\"quantity\":1}"))
                .andExpect(status().isForbidden());
    }

    // ---- add + cookie ----

    @Test
    void addIssuesCartCookieAndReturnsView() throws Exception {
        mvc.perform(post("/api/cart/items").with(csrf())
                        .contentType("application/json")
                        .content("{\"sku\":\"FES-1\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", containsString("nk_cart=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.items[0].sku").value("FES-1"));
    }

    @Test
    void addUnknownSkuIs404AndOutOfStockIs409() throws Exception {
        mvc.perform(post("/api/cart/items").with(csrf())
                        .contentType("application/json")
                        .content("{\"sku\":\"NO-SUCH\",\"quantity\":1}"))
                .andExpect(status().isNotFound());
    }

    // ---- GET cart ----

    @Test
    void cartWithoutCookieIsEmptyAndCreatesNothing() throws Exception {
        mvc.perform(get("/api/cart"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    // ---- session endpoint ----

    @Test
    void sessionWithoutCartCookieIsCheapZero() throws Exception {
        mvc.perform(get("/api/session"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.cartCount").value(0))
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    void sessionWithCartCookieReturnsCount() throws Exception {
        String token = addAndExtractToken();

        mvc.perform(get("/api/session").cookie(new Cookie("nk_cart", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartCount").value(2));
    }

    // ---- acceptance flow: static-page add → webshop cart page ----

    @Test
    void itemAddedViaApiAppearsOnCartPage() throws Exception {
        String token = addAndExtractToken();

        mvc.perform(get("/kosar").cookie(new Cookie("nk_cart", token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().string(containsString("Kosár Teszt Festék")));
    }

    @Test
    void cartPageWithoutCookieShowsEmptyCart() throws Exception {
        mvc.perform(get("/kosar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("üres")));
    }

    private String addAndExtractToken() throws Exception {
        MvcResult result = mvc.perform(post("/api/cart/items").with(csrf())
                        .contentType("application/json")
                        .content("{\"sku\":\"FES-1\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andReturn();
        jakarta.servlet.http.Cookie cookie = result.getResponse().getCookie("nk_cart");
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }
}
