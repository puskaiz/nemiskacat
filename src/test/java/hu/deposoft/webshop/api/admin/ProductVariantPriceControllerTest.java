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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = "webshop.admin.product-editor-enabled=true")
@Testcontainers
@Transactional
class ProductVariantPriceControllerTest {

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

    private long firstProductId() throws Exception {
        String body = mvc.perform(get("/api/admin/products").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode arr =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        return arr.get(0).path("id").asLong();
    }

    private long firstVariantId(long productId) throws Exception {
        String body = mvc.perform(get("/api/admin/products/" + productId).with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode node =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        return node.path("variants").get(0).path("id").asLong();
    }

    @Test void putPriceStoresGross() throws Exception {
        long pid = firstProductId();
        long vid = firstVariantId(pid);
        mvc.perform(put("/api/admin/products/" + pid + "/variants/" + vid + "/price")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"regular\":{\"amount\":1000,\"basis\":\"NET\"},\"sale\":null,\"saleFrom\":null,\"saleTo\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.variants[?(@.id == " + vid + ")].regularPriceHuf").value(org.hamcrest.Matchers.hasItem(1270)));
    }

    @Test void putPriceRejectsNegative() throws Exception {
        long pid = firstProductId(); long vid = firstVariantId(pid);
        mvc.perform(put("/api/admin/products/" + pid + "/variants/" + vid + "/price")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"regular\":{\"amount\":-5,\"basis\":\"GROSS\"},\"sale\":null,\"saleFrom\":null,\"saleTo\":null}"))
            .andExpect(status().isBadRequest());
    }

    @Test void putPriceRejectsSaleAboveRegular() throws Exception {
        long pid = firstProductId(); long vid = firstVariantId(pid);
        mvc.perform(put("/api/admin/products/" + pid + "/variants/" + vid + "/price")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"regular\":{\"amount\":1000,\"basis\":\"GROSS\"},\"sale\":{\"amount\":2000,\"basis\":\"GROSS\"},\"saleFrom\":null,\"saleTo\":null}"))
            .andExpect(status().isBadRequest());
    }

    @Test void putPriceUnknownVariantIs404() throws Exception {
        long pid = firstProductId();
        mvc.perform(put("/api/admin/products/" + pid + "/variants/999999/price")
                .with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"regular\":{\"amount\":1000,\"basis\":\"GROSS\"},\"sale\":null,\"saleFrom\":null,\"saleTo\":null}"))
            .andExpect(status().isNotFound());
    }

    @Test void putPriceForbiddenForNonAdmin() throws Exception {
        long pid = firstProductId(); long vid = firstVariantId(pid);
        mvc.perform(put("/api/admin/products/" + pid + "/variants/" + vid + "/price")
                .with(user("u").roles("USER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"regular\":{\"amount\":1000,\"basis\":\"GROSS\"},\"sale\":null,\"saleFrom\":null,\"saleTo\":null}"))
            .andExpect(status().isForbidden());
    }
}
