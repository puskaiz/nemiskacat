package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.catalog.CatalogQueryService.CategoryItemView;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for CatalogQueryService.cardsBySkus — the recommended-products
 * data source for the blog CMS. Runs against real Postgres via Testcontainers.
 */
@SpringBootTest
@Testcontainers
@Transactional
class CatalogCardsBySkusTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    CatalogQueryService catalog;

    @Autowired
    CatalogImporter importer;

    private SourceCategory cat(long wooId, String slug, String name, Long parentWooId) {
        return new SourceCategory(wooId, slug, name, parentWooId, 0, null, null, null);
    }

    private SourceProduct simpleProduct(long wooId, String slug, String name, String sku,
                                        List<Long> catIds, int stock) {
        return new SourceProduct(wooId, slug, name, "simple", "publish",
                null, null, null, null, null,
                catIds, List.of(),
                sku, 3700L, null, null, null, true, stock, 250,
                List.of(), List.of(), List.of());
    }

    @BeforeEach
    void seed() {
        importer.run(new SourceCatalog(
                List.of(cat(10L, "festekek", "Festékek", null)),
                List.<SourceAttribute>of(),
                List.of(
                        simpleProduct(100L, "in-stock-paint", "In Stock Paint", "A1", List.of(10L), 5),
                        simpleProduct(101L, "out-of-stock-paint", "Out Of Stock Paint", "B1", List.of(10L), 0)
                ), List.of()));
    }

    @Test
    void unknownSkuYieldsEmptyList() {
        assertThat(catalog.cardsBySkus(List.of("does-not-exist"))).isEmpty();
    }

    @Test
    void outOfStockProductIsOmitted_inStockKept_orderPreserved() {
        // B1 is out-of-stock (stock=0), A1 is in-stock (stock=5); both PUBLISHED.
        // cardsBySkus should omit B1 and return only A1, preserving requested order.
        List<CategoryItemView> cards = catalog.cardsBySkus(List.of("B1", "A1"));
        // B1 omitted (out of stock), A1 kept
        assertThat(cards).extracting(CategoryItemView::sku).containsExactly("A1");
        assertThat(cards).allMatch(CategoryItemView::available);
    }
}
