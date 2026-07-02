package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.catalog.ImageFetcher;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceAttributeValue;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceImage;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import hu.deposoft.webshop.integrations.woo.SourceVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * T6 acceptance: the product page is byte-identical for cookie-less visitors,
 * carries valid Product JSON-LD, micro-cache-ready headers, and renders the
 * out-of-stock state. URLs preserve the WP scheme (/product/{slug}).
 */
@SpringBootTest
@Testcontainers
@Transactional
class CatalogPagesTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    /** No network in tests: the catalog import now downloads images, so stub the fetcher. */
    @TestConfiguration
    static class StubFetcherConfig {
        @Bean
        @Primary
        ImageFetcher stubFetcher() {
            return url -> new ImageFetcher.FetchedImage(("IMG-" + url).getBytes(), "image/jpeg");
        }
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    CatalogImporter importer;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).build();
        importer.run(fixtureCatalog());
    }

    private SourceCatalog fixtureCatalog() {
        SourceProduct simple = new SourceProduct(100L, "pure-white-chalk-paint", "Pure White Chalk Paint",
                "simple", "publish", "Rövid leírás.", "<p>Hosszú <strong>leírás</strong>.</p>", null,
                "Pure White festék vásárlás", "Meta leírás.",
                List.of(10L), List.of(),
                "ASFPW", 3700L, null, null, null, true, 12, 250,
                List.of(), List.of(new SourceImage(900L, "wp/2023/05/pure-white.jpg", "Pure White doboz", 0, true)), List.of());

        SourceProduct outOfStock = new SourceProduct(101L, "elfogyott-termek", "Elfogyott Termék",
                "simple", "publish", null, null, null, null, null,
                List.of(10L), List.of(),
                "OOS-1", 5000L, null, null, null, true, 0, null,
                List.of(), List.of(), List.of());

        SourceProduct draft = new SourceProduct(102L, "piszkozat-termek", "Piszkozat",
                "simple", "draft", null, null, null, null, null,
                List.of(10L), List.of(),
                "DRAFT-1", 1000L, null, null, null, true, 3, null,
                List.of(), List.of(), List.of());

        SourceVariant v1 = new SourceVariant(201L, "WAX-S", 4500L, 3900L, null, null,
                true, 5, 120, 0, Map.of("kiszereles", "120-ml"));
        SourceVariant v2 = new SourceVariant(202L, "WAX-L", 7900L, null, null, null,
                true, 0, 500, 1, Map.of("kiszereles", "500-ml"));
        SourceProduct variable = new SourceProduct(103L, "soft-wax", "Soft Wax",
                "variable", "publish", null, null, null, null, null,
                List.of(10L), List.of("kiszereles"),
                null, null, null, null, null, true, 0, null,
                List.of(v1, v2), List.of(), List.of());

        return new SourceCatalog(
                List.of(new SourceCategory(10L, "festekek", "Festékek", null, 0, "Festék kategória.", null, null)),
                List.of(new SourceAttribute(1L, "kiszereles", "Kiszerelés", "select", List.of(
                        new SourceAttributeValue("120-ml", "120 ml", 0),
                        new SourceAttributeValue("500-ml", "500 ml", 1)))),
                List.of(simple, outOfStock, draft, variable), List.of());
    }

    // ---- product page ----

    @Test
    void productPageRendersWithJsonLd() throws Exception {
        mvc.perform(get("/product/pure-white-chalk-paint"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pure White Chalk Paint")))
                .andExpect(content().string(containsString("application/ld+json")))
                .andExpect(content().string(containsString("\"@type\":\"Product\"")))
                .andExpect(content().string(containsString("\"sku\":\"ASFPW\"")))
                .andExpect(content().string(containsString("\"price\":\"3700\"")))
                .andExpect(content().string(containsString("https://schema.org/InStock")));
    }

    @Test
    void productPageIsByteIdenticalAndSessionFree() throws Exception {
        MvcResult first = mvc.perform(get("/product/pure-white-chalk-paint"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().string("Cache-Control", "public, max-age=0, s-maxage=60"))
                .andReturn();
        MvcResult second = mvc.perform(get("/product/pure-white-chalk-paint")).andReturn();

        assertThat(first.getResponse().getContentAsByteArray())
                .isEqualTo(second.getResponse().getContentAsByteArray());
        assertThat(first.getRequest().getSession(false)).isNull();
    }

    @Test
    void trailingSlashVariantIsServed() throws Exception {
        mvc.perform(get("/product/pure-white-chalk-paint/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Pure White Chalk Paint")));
    }

    @Test
    void outOfStockStateIsRendered() throws Exception {
        mvc.perform(get("/product/elfogyott-termek"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Elfogyott")))
                .andExpect(content().string(containsString("https://schema.org/OutOfStock")));
    }

    @Test
    void variablePageShowsVariantLabelsAndAggregateOffer() throws Exception {
        mvc.perform(get("/product/soft-wax"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("120 ml")))
                .andExpect(content().string(containsString("AggregateOffer")))
                .andExpect(content().string(containsString("\"lowPrice\":\"3900\"")))
                .andExpect(content().string(containsString("\"highPrice\":\"7900\"")));
    }

    @Test
    void unknownSlugIs404() throws Exception {
        mvc.perform(get("/product/nincs-ilyen")).andExpect(status().isNotFound());
    }

    @Test
    void draftProductIs404() throws Exception {
        mvc.perform(get("/product/piszkozat-termek")).andExpect(status().isNotFound());
    }

    // ---- category page ----

    @Test
    void categoryPageListsPublishedProductsWithPrices() throws Exception {
        mvc.perform(get("/termekkategoria/festekek"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=0, s-maxage=60"))
                .andExpect(content().string(containsString("Festékek")))
                .andExpect(content().string(containsString("Pure White Chalk Paint")))
                .andExpect(content().string(containsString("Soft Wax")));
    }

    @Test
    void categoryPageDoesNotListDrafts() throws Exception {
        MvcResult result = mvc.perform(get("/termekkategoria/festekek")).andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("Piszkozat");
    }

    @Test
    void unknownCategoryIs404() throws Exception {
        mvc.perform(get("/termekkategoria/nincs-ilyen")).andExpect(status().isNotFound());
    }
}
