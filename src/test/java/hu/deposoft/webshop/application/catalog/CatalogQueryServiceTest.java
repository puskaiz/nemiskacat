package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.catalog.CatalogQueryService.CategoryItemView;
import hu.deposoft.webshop.application.catalog.CatalogQueryService.HomeCategoryView;
import hu.deposoft.webshop.application.catalog.CatalogQueryService.WorkshopListItemView;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.Product;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C1 acceptance: homepage query methods on CatalogQueryService.
 * Runs against real Postgres via Testcontainers.
 */
@SpringBootTest
@Testcontainers
@Transactional
class CatalogQueryServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    CatalogQueryService catalogQueryService;

    @Autowired
    CatalogImporter importer;

    @Autowired
    WorkshopService workshopService;

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    // ---- fixtures ----

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

    private SourceProduct draftProduct(long wooId, String slug, String name, String sku, List<Long> catIds) {
        return new SourceProduct(wooId, slug, name, "simple", "draft",
                null, null, null, null, null,
                catIds, List.of(),
                sku, 2000L, null, null, null, true, 5, 250,
                List.of(), List.of(), List.of());
    }

    @BeforeEach
    void seed() {
        // Top-level categories: 10 = Festékek, 20 = Viaszok
        // Sub-category: 11 = Kréta (child of 10)
        // Products: 2 published in Festékek, 1 out-of-stock in Festékek, 1 draft, 1 in Viaszok
        importer.run(new SourceCatalog(
                List.of(
                        cat(10L, "festekek", "Festékek", null),
                        cat(20L, "viaszok", "Viaszok", null),
                        cat(11L, "kretafestek", "Krétafesték", 10L)
                ),
                List.<SourceAttribute>of(),
                List.of(
                        simpleProduct(100L, "pure-white", "Pure White", "PW-1", List.of(10L), 5),
                        simpleProduct(101L, "old-white", "Old White", "OW-1", List.of(10L), 0),
                        simpleProduct(102L, "soft-wax", "Soft Wax", "SW-1", List.of(20L), 3),
                        draftProduct(103L, "draft-paint", "Draft Paint", "DP-1", List.of(10L))
                ), List.of()));
    }

    // ---- topLevelCategories ----

    @Test
    void topLevelCategoriesReturnsOnlyParentLessCategories() {
        List<HomeCategoryView> result = catalogQueryService.topLevelCategories();

        assertThat(result)
                .extracting(HomeCategoryView::name)
                .containsExactlyInAnyOrder("Festékek", "Viaszok");
        // sub-category "Krétafesték" must NOT appear
        assertThat(result).extracting(HomeCategoryView::name).doesNotContain("Krétafesték");
    }

    @Test
    void topLevelCategoriesProductCountsOnlyPublishedProducts() {
        List<HomeCategoryView> result = catalogQueryService.topLevelCategories();

        HomeCategoryView festekek = result.stream()
                .filter(c -> c.slug().equals("festekek")).findFirst().orElseThrow();
        // 2 published products (Pure White + Old White); draft is excluded
        assertThat(festekek.productCount()).isEqualTo(2L);

        HomeCategoryView viaszok = result.stream()
                .filter(c -> c.slug().equals("viaszok")).findFirst().orElseThrow();
        assertThat(viaszok.productCount()).isEqualTo(1L);
    }

    @Test
    void topLevelCategoriesDefaultIconKeyIsPaintCan() {
        List<HomeCategoryView> result = catalogQueryService.topLevelCategories();
        assertThat(result).allSatisfy(c -> assertThat(c.iconKey()).isEqualTo("paint-can"));
    }

    // ---- featuredProducts ----

    @Test
    void featuredProductsReturnsAtMostLimitItems() {
        List<CategoryItemView> result = catalogQueryService.featuredProducts(4);
        assertThat(result).hasSizeLessThanOrEqualTo(4);
    }

    @Test
    void featuredProductsReturnsOnlyAvailablePublishedProducts() {
        List<CategoryItemView> result = catalogQueryService.featuredProducts(10);

        // All returned items are available
        assertThat(result).allSatisfy(item -> assertThat(item.available()).isTrue());
        // Draft is excluded
        assertThat(result).extracting(CategoryItemView::name).doesNotContain("Draft Paint");
    }

    @Test
    void featuredProductsHonorsLimit() {
        List<CategoryItemView> all = catalogQueryService.featuredProducts(100);
        List<CategoryItemView> limited = catalogQueryService.featuredProducts(1);
        assertThat(limited).hasSizeLessThanOrEqualTo(1);
        assertThat(all.size()).isGreaterThanOrEqualTo(limited.size());
    }

    // ---- nextWorkshop ----

    @Test
    void nextWorkshopIsEmptyWhenNoWorkshopsExist() {
        Optional<WorkshopListItemView> result = catalogQueryService.nextWorkshop();
        assertThat(result).isEmpty();
    }

    @Test
    void nextWorkshopReturnsSoonestUpcomingWorkshop() {
        Product ws1 = workshopService.createWorkshop("Bútor workshop", "butor-workshop", "desc", 27);
        workshopService.addSession(ws1, NOW.plusDays(10), 5, 15_000L, "WS-A");

        Product ws2 = workshopService.createWorkshop("Fal workshop", "fal-workshop", "desc", 27);
        workshopService.addSession(ws2, NOW.plusDays(3), 5, 12_000L, "WS-B");

        Optional<WorkshopListItemView> result = catalogQueryService.nextWorkshop();

        assertThat(result).isPresent();
        // WS-B has the earlier date (plusDays(3)) so it should be first
        assertThat(result.get().name()).isEqualTo("Fal workshop");
        assertThat(result.get().hasUpcoming()).isTrue();
    }

    @Test
    void nextWorkshopIsEmptyWhenAllWorkshopsArePast() {
        Product ws = workshopService.createWorkshop("Múlt workshop", "mult-workshop", "desc", 27);
        workshopService.addSession(ws, NOW.minusDays(1), 5, 15_000L, "WS-PAST");

        Optional<WorkshopListItemView> result = catalogQueryService.nextWorkshop();
        assertThat(result).isEmpty();
    }

    // ---- product page ----

    /**
     * Imported workshops have no sessions/variants yet (sessions are added in admin). Rendering
     * the page must not blow up — previously jsonLd() called variants.getFirst() on an empty list.
     */
    @Test
    void productPageForWorkshopWithoutSessionsRendersWithoutError() {
        workshopService.createWorkshop("Új workshop", "uj-workshop", "<p>leírás</p>", 27);

        var result = catalogQueryService.productPage("uj-workshop");

        assertThat(result).isPresent();
        var view = result.orElseThrow();
        assertThat(view.workshop()).isTrue();
        assertThat(view.sessions()).isEmpty();
        // Product JSON-LD is still emitted (CLAUDE.md #7), with an out-of-stock availability.
        assertThat(view.jsonLd()).contains("OutOfStock");
    }
}
