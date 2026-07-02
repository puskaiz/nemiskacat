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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "webshop.admin.product-editor-enabled=false")
@Testcontainers
@Transactional
class ProductImageDisabledTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    ProductImageService imageService;

    @Autowired
    ProductAdminQueryService query;

    @Autowired
    CatalogImporter importer;

    @BeforeEach
    void seed() {
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint), List.of()));
    }

    private Long festekId() {
        return query.list(null, "fest", 0, 20).items().get(0).id();
    }

    @Test
    void uploadThrowsEditorDisabledException() {
        Long id = festekId();
        assertThatThrownBy(() -> imageService.upload(id, new byte[]{1, 2, 3}, "image/jpeg", "a.jpg"))
                .isInstanceOf(ProductAdminEditService.EditorDisabledException.class);
    }
}
