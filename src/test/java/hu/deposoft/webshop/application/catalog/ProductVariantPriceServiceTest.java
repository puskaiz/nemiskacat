package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "webshop.admin.product-editor-enabled=true")
@Testcontainers
@Transactional
class ProductVariantPriceServiceTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired ProductVariantPriceService priceService;
    @Autowired ProductAdminQueryService query;
    @Autowired hu.deposoft.webshop.application.catalog.CatalogImporter importer;

    @BeforeEach
    void seed() {
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

    private record Ids(Long productId, Long variantId) {}
    private Ids firstVariant() {
        Long pid = query.list(null, null, 0, 20).items().get(0).id();
        Long vid = query.detail(pid).variants().get(0).id();
        return new Ids(pid, vid);
    }
    private static ProductVariantPriceService.PriceInput net(long a)   { return new ProductVariantPriceService.PriceInput(a, ProductVariantPriceService.PriceBasis.NET); }
    private static ProductVariantPriceService.PriceInput gross(long a) { return new ProductVariantPriceService.PriceInput(a, ProductVariantPriceService.PriceBasis.GROSS); }
    private Long regularOf(ProductAdminQueryService.ProductDetailView d, Long vid) {
        return d.variants().stream().filter(x -> x.id().equals(vid)).findFirst().orElseThrow().regularPriceHuf();
    }
    private Long saleOf(ProductAdminQueryService.ProductDetailView d, Long vid) {
        return d.variants().stream().filter(x -> x.id().equals(vid)).findFirst().orElseThrow().salePriceHuf();
    }

    @Test void netInputStoredAsGross() {
        var ids = firstVariant();
        var d = priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(net(1000), null, null, null));
        assertThat(regularOf(d, ids.variantId())).isEqualTo(1270L); // 27% default
        assertThat(saleOf(d, ids.variantId())).isNull();
    }
    @Test void grossInputStoredVerbatim() {
        var ids = firstVariant();
        var d = priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(gross(2000), null, null, null));
        assertThat(regularOf(d, ids.variantId())).isEqualTo(2000L);
    }
    @Test void saleSetThenCleared() {
        var ids = firstVariant();
        priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(gross(2000), gross(1500), null, null));
        var d = priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(gross(2000), null, null, null));
        assertThat(saleOf(d, ids.variantId())).isNull();
    }
    @Test void rejectsNegative() {
        var ids = firstVariant();
        assertThatThrownBy(() -> priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(gross(-1), null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsSaleAboveRegular() {
        var ids = firstVariant();
        assertThatThrownBy(() -> priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(gross(1000), gross(2000), null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsInvertedWindow() {
        var ids = firstVariant();
        var from = java.time.OffsetDateTime.parse("2026-07-01T00:00:00Z");
        var to   = java.time.OffsetDateTime.parse("2026-06-01T00:00:00Z");
        assertThatThrownBy(() -> priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(gross(2000), gross(1500), from, to)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void variantNotOnProductThrowsNotFound() {
        Long pid = query.list(null, null, 0, 20).items().get(0).id();
        assertThatThrownBy(() -> priceService.updatePrice(pid, 999999L,
                new ProductVariantPriceService.PriceUpdate(gross(100), null, null, null)))
                .isInstanceOf(hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException.class);
    }
    @Test void nullRegularClearsEverything() {
        var ids = firstVariant();
        priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(gross(2000), gross(1500),
                        java.time.OffsetDateTime.parse("2026-06-01T00:00:00Z"),
                        java.time.OffsetDateTime.parse("2026-07-01T00:00:00Z")));
        var d = priceService.updatePrice(ids.productId(), ids.variantId(),
                new ProductVariantPriceService.PriceUpdate(null, null, null, null));
        assertThat(regularOf(d, ids.variantId())).isNull();
        assertThat(saleOf(d, ids.variantId())).isNull();
    }
}
