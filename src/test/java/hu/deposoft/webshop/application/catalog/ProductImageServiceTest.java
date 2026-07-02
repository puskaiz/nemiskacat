package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.domain.catalog.ProductImage;
import hu.deposoft.webshop.domain.catalog.ProductImageRepository;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
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
class ProductImageServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    ProductImageService imageService;

    @Autowired
    ProductAdminQueryService query;

    @Autowired
    CatalogImporter importer;

    @Autowired
    ProductImageRepository imageRepo;

    @Autowired
    ProductRepository productRepo;

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
    void uploadAppendsImageAndFirstBecomesCover() {
        Long id = festekId();
        byte[] bytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}; // minimal JPEG header
        ProductAdminQueryService.ProductDetailView view = imageService.upload(id, bytes, "image/jpeg", "a.jpg");
        assertThat(view.images()).hasSize(1);
        assertThat(view.images().get(0).url()).startsWith("/media/up/");
    }

    @Test
    void rejectsNonImageType() {
        Long id = festekId();
        assertThatThrownBy(() -> imageService.upload(id, new byte[]{1, 2, 3}, "application/pdf", "x.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteRemovesImage() {
        Long id = festekId();
        ProductAdminQueryService.ProductDetailView view =
                imageService.upload(id, new byte[]{(byte) 0xFF, (byte) 0xD8}, "image/jpeg", "b.jpg");
        Long imageId = view.images().get(0).id();
        ProductAdminQueryService.ProductDetailView after = imageService.delete(id, imageId);
        assertThat(after.images()).isEmpty();
    }

    @Test
    void reorderAndCover() {
        Long id = festekId();
        ProductAdminQueryService.ProductDetailView v1 =
                imageService.upload(id, new byte[]{1}, "image/jpeg", "first.jpg");
        Long firstId = v1.images().get(0).id();
        ProductAdminQueryService.ProductDetailView v2 =
                imageService.upload(id, new byte[]{2}, "image/png", "second.png");
        Long secondId = v2.images().stream()
                .filter(i -> !i.id().equals(firstId)).findFirst().orElseThrow().id();

        ProductAdminQueryService.ProductDetailView after = imageService.setCover(id, secondId);
        assertThat(after.images().get(0).id()).isEqualTo(secondId);
    }

    @Test
    void reorderHappyPath() {
        Long id = festekId();
        ProductAdminQueryService.ProductDetailView v1 =
                imageService.upload(id, new byte[]{1}, "image/jpeg", "first.jpg");
        Long firstId = v1.images().get(0).id();
        ProductAdminQueryService.ProductDetailView v2 =
                imageService.upload(id, new byte[]{2}, "image/png", "second.png");
        Long secondId = v2.images().stream()
                .filter(i -> !i.id().equals(firstId)).findFirst().orElseThrow().id();

        // Reverse the order
        ProductAdminQueryService.ProductDetailView after = imageService.reorder(id, List.of(secondId, firstId));

        assertThat(after.images().stream().map(ProductAdminQueryService.ImageView::id).toList())
                .containsExactly(secondId, firstId);
    }

    @Test
    void reorderRejectsWrongIdSet() {
        Long id = festekId();
        ProductAdminQueryService.ProductDetailView v1 =
                imageService.upload(id, new byte[]{1}, "image/jpeg", "first.jpg");
        Long realId = v1.images().get(0).id();
        Long foreignId = realId + 9999L; // guaranteed not in the product

        assertThatThrownBy(() -> imageService.reorder(id, List.of(realId, foreignId)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteOfCoverReassignsFeatured() {
        Long id = festekId();
        // Upload A (cover, pos 0, featured) then B (pos 1, not featured)
        ProductAdminQueryService.ProductDetailView v1 =
                imageService.upload(id, new byte[]{1}, "image/jpeg", "a.jpg");
        Long aId = v1.images().get(0).id();
        imageService.upload(id, new byte[]{2}, "image/png", "b.jpg");

        // Delete A (the cover)
        imageService.delete(id, aId);

        // Reload the product's images ordered by position
        var product = productRepo.findById(id).orElseThrow();
        List<ProductImage> remaining = imageRepo.findByProductOrderByPositionAsc(product);

        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).isFeatured()).isTrue();
        assertThat(remaining.get(0).getPosition()).isEqualTo(0);
    }

    @Test
    void reorderMovesFeaturedToNewFirst() {
        Long id = festekId();
        // Upload A (pos 0, featured) then B (pos 1)
        ProductAdminQueryService.ProductDetailView v1 =
                imageService.upload(id, new byte[]{1}, "image/jpeg", "a.jpg");
        Long aId = v1.images().get(0).id();
        ProductAdminQueryService.ProductDetailView v2 =
                imageService.upload(id, new byte[]{2}, "image/png", "b.jpg");
        Long bId = v2.images().stream().filter(i -> !i.id().equals(aId)).findFirst().orElseThrow().id();

        // Reorder so B is first
        imageService.reorder(id, List.of(bId, aId));

        var product = productRepo.findById(id).orElseThrow();
        List<ProductImage> ordered = imageRepo.findByProductOrderByPositionAsc(product);

        assertThat(ordered).hasSize(2);
        assertThat(ordered.get(0).getId()).isEqualTo(bId);
        assertThat(ordered.get(0).getPosition()).isEqualTo(0);
        assertThat(ordered.get(0).isFeatured()).isTrue();
        assertThat(ordered.get(1).getId()).isEqualTo(aId);
        assertThat(ordered.get(1).getPosition()).isEqualTo(1);
        assertThat(ordered.get(1).isFeatured()).isFalse();
        // Exactly one featured image
        assertThat(ordered.stream().filter(ProductImage::isFeatured)).hasSize(1);
    }
}
