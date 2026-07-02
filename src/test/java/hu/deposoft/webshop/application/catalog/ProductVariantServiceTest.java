package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.domain.catalog.AttributeRepository;
import hu.deposoft.webshop.domain.catalog.AttributeValueRepository;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import java.util.ArrayList;
import java.util.Collections;
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
class ProductVariantServiceTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired ProductAdminQueryService query;
    @Autowired AttributeRepository attributeRepo;
    @Autowired AttributeValueRepository attributeValueRepo;
    @Autowired ProductVariantService variantService;
    @Autowired CatalogImporter importer;

    Long pirosId, kekId;

    @BeforeEach
    void seed() {
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

        var szin = hu.deposoft.webshop.domain.catalog.Attribute.create(null, "szin", "Szín", "select");
        var piros = hu.deposoft.webshop.domain.catalog.AttributeValue.create(szin, "piros", "Piros", 0);
        var kek   = hu.deposoft.webshop.domain.catalog.AttributeValue.create(szin, "kek", "Kék", 1);
        szin.getValues().add(piros); szin.getValues().add(kek);
        attributeRepo.save(szin);
        pirosId = piros.getId(); kekId = kek.getId();
    }

    private Long firstProductId() { return query.list(null, null, 0, 20).items().get(0).id(); }

    @Test void createAppendsVariantWithComboAndLabel() {
        Long pid = firstProductId();
        var d = variantService.createVariant(pid, new ProductVariantService.CreateVariant("SKU-RED", java.util.List.of(pirosId)));
        var created = d.variants().stream().filter(v -> "SKU-RED".equals(v.sku())).findFirst().orElseThrow();
        assertThat(created.label()).isEqualTo("Piros");
        assertThat(created.attributeValues()).extracting(ProductAdminQueryService.AttributeValueRef::valueLabel).containsExactly("Piros");
    }

    @Test void rejectsDuplicateSku() {
        Long pid = firstProductId();
        variantService.createVariant(pid, new ProductVariantService.CreateVariant("DUP", java.util.List.of(pirosId)));
        assertThatThrownBy(() -> variantService.createVariant(pid, new ProductVariantService.CreateVariant("DUP", java.util.List.of(kekId))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsDuplicateCombo() {
        Long pid = firstProductId();
        variantService.createVariant(pid, new ProductVariantService.CreateVariant(null, java.util.List.of(pirosId)));
        assertThatThrownBy(() -> variantService.createVariant(pid, new ProductVariantService.CreateVariant(null, java.util.List.of(pirosId))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsUnknownAttributeValue() {
        Long pid = firstProductId();
        assertThatThrownBy(() -> variantService.createVariant(pid, new ProductVariantService.CreateVariant(null, java.util.List.of(999999L))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsTwoValuesOfSameAttribute() {
        Long pid = firstProductId();
        assertThatThrownBy(() -> variantService.createVariant(pid, new ProductVariantService.CreateVariant(null, java.util.List.of(pirosId, kekId))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void updatesComboAndSku() {
        Long pid = firstProductId();
        var d0 = variantService.createVariant(pid, new ProductVariantService.CreateVariant("A", java.util.List.of(pirosId)));
        Long vid = d0.variants().stream().filter(v -> "A".equals(v.sku())).findFirst().orElseThrow().id();
        var d1 = variantService.updateVariant(pid, vid, new ProductVariantService.UpdateVariant("B", java.util.List.of(kekId)));
        var v = d1.variants().stream().filter(x -> x.id().equals(vid)).findFirst().orElseThrow();
        assertThat(v.sku()).isEqualTo("B");
        assertThat(v.attributeValues()).extracting(ProductAdminQueryService.AttributeValueRef::valueLabel).containsExactly("Kék");
    }

    @Test void deleteRemovesVariant() {
        Long pid = firstProductId();
        var d0 = variantService.createVariant(pid, new ProductVariantService.CreateVariant("X", java.util.List.of(pirosId)));
        int before = d0.variants().size();
        Long vid = d0.variants().stream().filter(v -> "X".equals(v.sku())).findFirst().orElseThrow().id();
        var d1 = variantService.deleteVariant(pid, vid);
        assertThat(d1.variants()).hasSize(before - 1);
        assertThat(d1.variants()).noneMatch(v -> v.id().equals(vid));
    }

    @Test void rejectsDeletingLastVariant() {
        Long pid = firstProductId();
        Long onlyId = query.detail(pid).variants().get(0).id();
        assertThatThrownBy(() -> variantService.deleteVariant(pid, onlyId)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void reorderSetsPositions() {
        Long pid = firstProductId();
        variantService.createVariant(pid, new ProductVariantService.CreateVariant("S1", java.util.List.of(pirosId)));
        var d1 = variantService.createVariant(pid, new ProductVariantService.CreateVariant("S2", java.util.List.of(kekId)));
        var ids = d1.variants().stream().map(ProductAdminQueryService.VariantView::id).toList();
        var reversed = new java.util.ArrayList<>(ids); java.util.Collections.reverse(reversed);
        var d2 = variantService.reorderVariants(pid, reversed);
        assertThat(d2.variants().stream().map(ProductAdminQueryService.VariantView::id).toList()).isEqualTo(reversed);
    }
}
