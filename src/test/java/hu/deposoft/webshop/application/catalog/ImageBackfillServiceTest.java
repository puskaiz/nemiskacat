package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductImage;
import hu.deposoft.webshop.domain.catalog.ProductImageRepository;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.ProductStatus;
import hu.deposoft.webshop.domain.catalog.ProductType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Backfill acceptance: existing catalog rows still carrying wp/ hot-link keys (gallery
 * images) or hot-linked inline &lt;img&gt; in descriptions are pulled into local storage
 * and rewritten to /media/…, like the blog. Re-running is a no-op (idempotent).
 */
@SpringBootTest
@Testcontainers
@Transactional
class ImageBackfillServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @TestConfiguration
    static class StubFetcherConfig {
        static final Map<String, byte[]> BYTES = new ConcurrentHashMap<>();

        @Bean
        @Primary
        ImageFetcher stubFetcher() {
            return url -> new ImageFetcher.FetchedImage(
                    BYTES.computeIfAbsent(url, u -> ("IMG-" + u).getBytes(StandardCharsets.UTF_8)),
                    "image/jpeg");
        }
    }

    @Autowired
    ImageBackfillService service;

    @Autowired
    ProductRepository products;

    @Autowired
    ProductImageRepository images;

    @Test
    void relocatesWpGalleryKeysAndRewritesDescriptions() {
        Product p = Product.create(700100L, "legacy-product", "Legacy Product",
                ProductType.SIMPLE, ProductStatus.PUBLISHED);
        p.setDescription("<p>Régi</p>"
                + "<img src=\"https://nemiskacat.hu/wp-content/uploads/2022/03/legacy.jpg\">");
        p = products.save(p);
        images.save(ProductImage.create(p, "wp/2022/03/legacy-main.jpg", "alt", 0, true));

        ImageBackfillService.Report report = service.run();

        assertThat(report.galleryRelocated()).isEqualTo(1);
        assertThat(report.descriptionsRewritten()).isEqualTo(1);
        assertThat(report.errors()).isEmpty();

        Product reloaded = products.findByExternalId(700100L).orElseThrow();
        assertThat(reloaded.getDescription()).contains("/media/up/").doesNotContain("wp-content/uploads");
        ProductImage img = images.findByProductOrderByPositionAsc(reloaded).getFirst();
        assertThat(img.getStorageKey()).startsWith("up/");
    }

    @Test
    void reRunIsANoOp() {
        Product p = Product.create(700200L, "legacy-idem", "Legacy Idempotent",
                ProductType.SIMPLE, ProductStatus.PUBLISHED);
        p.setDescription("<img src=\"https://nemiskacat.hu/wp-content/uploads/2022/03/i.jpg\">");
        p = products.save(p);
        images.save(ProductImage.create(p, "wp/2022/03/idem.jpg", "", 0, true));

        service.run();
        ImageBackfillService.Report second = service.run();

        assertThat(second.galleryRelocated()).isZero();
        assertThat(second.descriptionsRewritten()).isZero();
    }
}
