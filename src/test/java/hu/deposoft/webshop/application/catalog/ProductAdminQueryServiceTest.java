package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.domain.catalog.AttributeRepository;
import hu.deposoft.webshop.domain.catalog.AttributeValueRepository;
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

@SpringBootTest
@Testcontainers
@Transactional
class ProductAdminQueryServiceTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired ProductAdminQueryService products;
    @Autowired hu.deposoft.webshop.application.catalog.CatalogImporter importer;
    @Autowired AttributeRepository attributeRepo;
    @Autowired AttributeValueRepository attributeValueRepo;

    @BeforeEach
    void seed() {
        // paint belongs to two categories: "Kat" and "Zsindely" (the latter sorts after Kat,
        // so the alphabetically-first stays "Kat"); stamp belongs only to "Kat".
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L, 20L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of(), List.of());
        SourceProduct stamp = new SourceProduct(101L, "pecset", "Pecsét", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "PEC-1", 1900L, null, null, null, true, 4, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null),
                        new SourceCategory(20L, "zsindely", "Zsindely", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint, stamp), List.of()));
    }

    @Test
    void listReturnsProductsWithDefaultVariantPriceAndStock() {
        ProductAdminQueryService.PageResult page = products.list(null, null, 0, 20);
        assertThat(page.total()).isEqualTo(2);
        ProductAdminQueryService.ProductSummary fes = page.items().stream()
                .filter(p -> p.slug().equals("festek")).findFirst().orElseThrow();
        assertThat(fes.name()).isEqualTo("Festék");
        assertThat(fes.priceGrossHuf()).isEqualTo(3700L);
        assertThat(fes.stockQty()).isEqualTo(10);
        assertThat(fes.primaryCategory()).isEqualTo("Kat");
        assertThat(fes.variantCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void listFiltersByCategoryAndQuery() {
        assertThat(products.list(List.of("kat"), null, 0, 20).total()).isEqualTo(2);
        assertThat(products.list(null, "pecs", 0, 20).items())
                .singleElement().satisfies(p -> assertThat(p.slug()).isEqualTo("pecset"));
        assertThat(products.list(List.of("nincs-ilyen"), null, 0, 20).total()).isZero();
    }

    @Test
    void listFiltersByAnyOfMultipleCategories() {
        // only paint is in "zsindely"
        assertThat(products.list(List.of("zsindely"), null, 0, 20).items())
                .singleElement().satisfies(p -> assertThat(p.slug()).isEqualTo("festek"));
        // OR semantics: zsindely (paint) ∪ kat (both) = both products, no duplicate row
        assertThat(products.list(List.of("zsindely", "kat"), null, 0, 20).total()).isEqualTo(2);
    }

    @Test
    void summaryExposesAllCategoriesAlphabetically() {
        ProductAdminQueryService.ProductSummary fes = products.list(null, "fest", 0, 20).items().get(0);
        assertThat(fes.primaryCategory()).isEqualTo("Kat");
        assertThat(fes.categories()).extracting(ProductAdminQueryService.CategoryRef::name)
                .containsExactly("Kat", "Zsindely");
    }

    @Test
    void detailMapsVariantsCategoriesAndStatus() {
        Long id = products.list(null, "fest", 0, 20).items().get(0).id();
        ProductAdminQueryService.ProductDetailView d = products.detail(id);
        assertThat(d.name()).isEqualTo("Festék");
        assertThat(d.slug()).isEqualTo("festek");
        assertThat(d.variants()).isNotEmpty();
        assertThat(d.variants().get(0).sku()).isEqualTo("FES-1");
        assertThat(d.variants().get(0).regularPriceHuf()).isEqualTo(3700L);
        assertThat(d.categories()).extracting(ProductAdminQueryService.CategoryRef::name).contains("Kat");
    }

    @Test
    void detailThrowsForUnknownId() {
        assertThatThrownBy(() -> products.detail(999999L))
                .isInstanceOf(hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException.class);
    }

    @Test
    void detailExposesEffectiveVatRate() {
        Long id = products.list(null, null, 0, 20).items().get(0).id();
        var detail = products.detail(id);
        assertThat(detail.effectiveVatRatePercent()).isEqualTo(27);
    }

    @Test void attributesCatalogListsValuesSortedBySortOrder() {
        var szin = hu.deposoft.webshop.domain.catalog.Attribute.create(null, "szin", "Szín", "select");
        szin.getValues().add(hu.deposoft.webshop.domain.catalog.AttributeValue.create(szin, "kek", "Kék", 1));
        szin.getValues().add(hu.deposoft.webshop.domain.catalog.AttributeValue.create(szin, "piros", "Piros", 0));
        attributeRepo.save(szin);
        var cat = products.attributes();
        var view = cat.stream().filter(a -> a.slug().equals("szin")).findFirst().orElseThrow();
        assertThat(view.label()).isEqualTo("Szín");
        assertThat(view.values()).extracting(ProductAdminQueryService.AttributeValueOption::label).containsExactly("Piros", "Kék");
    }

    @Test void variantViewExposesAttributeComboField() {
        Long pid = products.list(null, null, 0, 20).items().get(0).id();
        var detail = products.detail(pid);
        assertThat(detail.variants().get(0).attributeValues()).isNotNull();
    }
}
