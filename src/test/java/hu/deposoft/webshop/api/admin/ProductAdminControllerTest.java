package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = "webshop.admin.product-editor-enabled=true")
@Testcontainers
@Transactional
class ProductAdminControllerTest {

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
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of(), List.of());
        SourceProduct stamp = new SourceProduct(101L, "pecset", "Pecsét", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "PEC-1", 1900L, null, null, null, true, 4, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint, stamp), List.of()));
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor admin() {
        return user("a@example.com").roles("ADMIN");
    }

    @Test
    void listReturnsProductsWithTotalCountHeader() throws Exception {
        mvc.perform(get("/api/admin/products").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Total-Count"))
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(content().string(containsString("Festék")));
    }

    @Test
    void listRejectsNonAdmin() throws Exception {
        mvc.perform(get("/api/admin/products").with(user("c@example.com").roles("CUSTOMER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void detailReturnsVariants() throws Exception {
        Long id = resolveProductId("festek");
        mvc.perform(get("/api/admin/products/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Festék"))
                .andExpect(jsonPath("$.variants[0].sku").exists())
                .andExpect(jsonPath("$.variants[0].sku").value("FES-1"));
    }

    @Test
    void detailReturns404ForUnknownId() throws Exception {
        mvc.perform(get("/api/admin/products/999999").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void categoriesEndpointReturnsList() throws Exception {
        mvc.perform(get("/api/admin/categories").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").exists())
                .andExpect(content().string(containsString("kat")));
    }

    @Test
    void updatePersistsContent() throws Exception {
        Long id = resolveProductId("festek");
        String body = "{\"name\":\"Festék Pro\",\"shortDescription\":\"rövid\",\"description\":\"hosszú\","
                + "\"seoTitle\":\"SEO\",\"metaDescription\":\"meta\",\"status\":\"DRAFT\",\"categorySlugs\":[\"kat\"]}";
        mvc.perform(put("/api/admin/products/{id}", id).with(admin()).with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Festék Pro"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.categories[0].slug").value("kat"));
    }

    @Test
    void updateRejectsBlankName() throws Exception {
        Long id = resolveProductId("festek");
        String body = "{\"name\":\"   \",\"status\":\"PUBLISHED\",\"categorySlugs\":[]}";
        mvc.perform(put("/api/admin/products/{id}", id).with(admin()).with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateForbiddenForNonAdmin() throws Exception {
        Long id = resolveProductId("festek");
        String body = "{\"name\":\"X\",\"status\":\"PUBLISHED\",\"categorySlugs\":[]}";
        mvc.perform(put("/api/admin/products/{id}", id).with(user("c@example.com").roles("CUSTOMER")).with(csrf())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    /** Resolve a product id by slug via the list endpoint's JSON, mirroring the plan's id-resolution. */
    private Long resolveProductId(String slug) throws Exception {
        String body = mvc.perform(get("/api/admin/products").with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode arr =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        for (com.fasterxml.jackson.databind.JsonNode n : arr) {
            if (slug.equals(n.path("slug").asText())) {
                return n.path("id").asLong();
            }
        }
        throw new IllegalStateException("No product with slug " + slug);
    }
}
