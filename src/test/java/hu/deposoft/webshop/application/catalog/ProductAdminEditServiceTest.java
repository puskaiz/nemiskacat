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
class ProductAdminEditServiceTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired ProductAdminEditService edit;
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

    private Long festekId() { return query.list(null, "fest", 0, 20).items().get(0).id(); }

    @Test
    void updatesContentFieldsAndCategories() {
        Long id = festekId();
        var view = edit.updateContent(id, new ProductAdminEditService.ContentUpdate(
                "Festék Pro", "rövid", "hosszú leírás", "SEO cím", "meta",
                hu.deposoft.webshop.domain.catalog.ProductStatus.DRAFT, java.util.List.of("kat")));
        assertThat(view.name()).isEqualTo("Festék Pro");
        assertThat(view.status()).isEqualTo(hu.deposoft.webshop.domain.catalog.ProductStatus.DRAFT);
        assertThat(view.description()).isEqualTo("hosszú leírás");
        assertThat(view.categories()).extracting(ProductAdminQueryService.CategoryRef::slug).containsExactly("kat");
    }

    @Test
    void rejectsBlankName() {
        Long id = festekId();
        assertThatThrownBy(() -> edit.updateContent(id, new ProductAdminEditService.ContentUpdate(
                "  ", null, null, null, null, hu.deposoft.webshop.domain.catalog.ProductStatus.PUBLISHED, java.util.List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownCategory() {
        Long id = festekId();
        assertThatThrownBy(() -> edit.updateContent(id, new ProductAdminEditService.ContentUpdate(
                "X", null, null, null, null, hu.deposoft.webshop.domain.catalog.ProductStatus.PUBLISHED, java.util.List.of("nope"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
