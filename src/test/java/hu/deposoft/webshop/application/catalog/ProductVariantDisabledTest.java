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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "webshop.admin.product-editor-enabled=false")
@Testcontainers
@Transactional
class ProductVariantDisabledTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired ProductAdminQueryService query;
    @Autowired AttributeRepository attributeRepo;
    @Autowired AttributeValueRepository attributeValueRepo;
    @Autowired ProductVariantService variantService;
    @Autowired CatalogImporter importer;

    Long pirosId;

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

        var szin = hu.deposoft.webshop.domain.catalog.Attribute.create(null, "szin", "Szín", "select");
        var piros = hu.deposoft.webshop.domain.catalog.AttributeValue.create(szin, "piros", "Piros", 0);
        var kek   = hu.deposoft.webshop.domain.catalog.AttributeValue.create(szin, "kek", "Kék", 1);
        szin.getValues().add(piros); szin.getValues().add(kek);
        attributeRepo.save(szin);
        pirosId = piros.getId();
    }

    @Test void createThrowsWhenEditorDisabled() {
        Long pid = query.list(null, null, 0, 20).items().get(0).id();
        assertThatThrownBy(() -> variantService.createVariant(pid,
                new ProductVariantService.CreateVariant("Z", java.util.List.of(pirosId))))
                .isInstanceOf(hu.deposoft.webshop.application.catalog.ProductAdminEditService.EditorDisabledException.class);
    }
}
