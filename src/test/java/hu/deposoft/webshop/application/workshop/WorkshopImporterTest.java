package hu.deposoft.webshop.application.workshop;

import hu.deposoft.webshop.application.catalog.ImageFetcher;
import hu.deposoft.webshop.domain.catalog.FulfilmentType;
import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductImage;
import hu.deposoft.webshop.domain.catalog.ProductImageRepository;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.ProductType;
import hu.deposoft.webshop.integrations.woo.SourceWorkshop;
import hu.deposoft.webshop.integrations.woo.SourceWorkshopImage;
import hu.deposoft.webshop.integrations.woo.SourceWorkshops;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance: importing workshops creates type=WORKSHOP products (vat=27) with their
 * gallery images, and re-running is idempotent (no duplicate workshops or images,
 * name/description updated, slug preserved). Runs against real Postgres. No network:
 * a stub {@link ImageFetcher} returns fixed bytes per URL.
 */
@SpringBootTest
@Testcontainers
@Transactional
class WorkshopImporterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    /**
     * Stub fetcher: maps each URL to a deterministic tiny PNG byte[] so storage keys
     * (content-addressed) are stable across runs — distinct URLs get distinct bytes.
     */
    @TestConfiguration
    static class StubFetcherConfig {

        static final AtomicInteger FETCH_CALLS = new AtomicInteger();
        // distinct, deterministic bytes per URL → distinct content-addressed storage keys
        static final Map<String, byte[]> BYTES = new ConcurrentHashMap<>();

        @Bean
        @Primary
        ImageFetcher stubFetcher() {
            return url -> {
                FETCH_CALLS.incrementAndGet();
                byte[] bytes = BYTES.computeIfAbsent(url,
                        u -> ("PNG-" + u).getBytes(StandardCharsets.UTF_8));
                return new ImageFetcher.FetchedImage(bytes, "image/png");
            };
        }
    }

    @Autowired
    WorkshopImporter importer;

    @Autowired
    ProductRepository products;

    @Autowired
    ProductImageRepository images;

    // ---- fixtures ----

    private SourceWorkshop workshop(long externalId, String slug, String name, String descriptionHtml) {
        return new SourceWorkshop(externalId, slug, name, descriptionHtml, List.of(
                new SourceWorkshopImage("https://workshop.example/" + slug + "/a.jpg", 0),
                new SourceWorkshopImage("https://workshop.example/" + slug + "/b.png", 1)));
    }

    private SourceWorkshops twoWorkshops(String suffix) {
        return new SourceWorkshops(List.of(
                workshop(43532L, "butorfestes-alapok-workshop",
                        "Bútorfestés alapok workshop" + suffix,
                        "<p>Szint: Kezdő</p><h2>Miért ajánlom?</h2>" + suffix),
                workshop(40301L, "annie-sloan-kisbutorfesto-workshop",
                        "Annie Sloan kisbútorfestő workshop" + suffix,
                        "<p>Kisbútor</p>" + suffix)));
    }

    // ---- tests ----

    @Test
    void freshImportCreatesWorkshopsWithGallery() {
        WorkshopImportReport report = importer.run(twoWorkshops(""));

        assertThat(report.workshopsCreated()).isEqualTo(2);
        assertThat(report.imagesCreated()).isEqualTo(4);
        assertThat(report.errors()).isEmpty();

        Product ws = products.findByExternalId(43532L).orElseThrow();
        assertThat(ws.getType()).isEqualTo(ProductType.WORKSHOP);
        assertThat(ws.getVatRatePercent()).isEqualTo(27);
        assertThat(ws.getInvoiceSource()).isEqualTo(InvoiceSource.BILLINGO);
        assertThat(ws.getFulfilmentType()).isEqualTo(FulfilmentType.EVENT);
        assertThat(ws.getSlug()).isEqualTo("butorfestes-alapok-workshop");
        assertThat(ws.getName()).isEqualTo("Bútorfestés alapok workshop");
        assertThat(ws.getDescription()).isEqualTo("<p>Szint: Kezdő</p><h2>Miért ajánlom?</h2>");
        // workshops never get sessions/variants on import
        assertThat(ws.getVariants()).isEmpty();

        List<ProductImage> gallery = images.findByProductOrderByPositionAsc(ws);
        assertThat(gallery).hasSize(2);
        assertThat(gallery.get(0).getPosition()).isZero();
        assertThat(gallery.get(0).isFeatured()).isTrue();
        assertThat(gallery.get(1).getPosition()).isEqualTo(1);
        assertThat(gallery.get(1).isFeatured()).isFalse();
        // the two slides have distinct (content-addressed) storage keys
        assertThat(gallery.get(0).getStorageKey()).isNotEqualTo(gallery.get(1).getStorageKey());
    }

    @Test
    void descriptionInlineImagesAreRewrittenToLocalMedia() {
        // The workshop description embeds standalone <img> widgets hot-linked to the
        // source site (workshop.nemiskacat.hu); the importer must pull them into storage
        // and rewrite src to /media/…, like the blog import.
        String descriptionHtml = "<h3>Galéria</h3>"
                + "<img src=\"https://workshop.nemiskacat.hu/wp-content/uploads/2023/09/inline.jpg\" alt=\"\">";
        SourceWorkshops source = new SourceWorkshops(List.of(new SourceWorkshop(
                50001L, "inline-image-workshop", "Inline Image Workshop", descriptionHtml, List.of())));

        WorkshopImportReport report = importer.run(source);

        assertThat(report.errors()).isEmpty();
        Product ws = products.findByExternalId(50001L).orElseThrow();
        assertThat(ws.getDescription())
                .contains("/media/up/")
                .doesNotContain("wp-content/uploads");
    }

    @Test
    void reimportIsIdempotentUpdatingNameAndDescriptionButNotSlug() {
        importer.run(twoWorkshops(""));

        long workshopCountBefore = products.findByType(ProductType.WORKSHOP).size();
        Product before = products.findByExternalId(43532L).orElseThrow();
        long imageCountBefore = images.findByProductOrderByPositionAsc(before).size();

        WorkshopImportReport second = importer.run(twoWorkshops(" v2"));

        // no new workshops, no new images
        assertThat(second.workshopsCreated()).isZero();
        assertThat(second.workshopsUpdated()).isEqualTo(2);
        assertThat(second.imagesCreated()).isZero();
        assertThat(products.findByType(ProductType.WORKSHOP)).hasSize((int) workshopCountBefore);
        assertThat(workshopCountBefore).isEqualTo(2);

        Product after = products.findByExternalId(43532L).orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getName()).isEqualTo("Bútorfestés alapok workshop v2");
        assertThat(after.getDescription()).isEqualTo("<p>Szint: Kezdő</p><h2>Miért ajánlom?</h2> v2");
        // slug is immutable (CLAUDE.md #7)
        assertThat(after.getSlug()).isEqualTo("butorfestes-alapok-workshop");
        // gallery unchanged (no duplicates)
        assertThat(images.findByProductOrderByPositionAsc(after)).hasSize((int) imageCountBefore).hasSize(2);
    }
}
