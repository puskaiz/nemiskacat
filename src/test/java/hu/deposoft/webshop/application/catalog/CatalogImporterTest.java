package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.domain.catalog.Category;
import hu.deposoft.webshop.domain.catalog.CategoryRepository;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductImage;
import hu.deposoft.webshop.domain.catalog.ProductImageRepository;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.ProductTag;
import hu.deposoft.webshop.domain.catalog.ProductTagRepository;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.catalog.VariantRepository;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceAttributeValue;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceImage;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import hu.deposoft.webshop.integrations.woo.SourceTag;
import hu.deposoft.webshop.integrations.woo.SourceVariant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T4 acceptance: importing twice creates no duplicates; Woo-side changes (renamed
 * product, new product) arrive on the next run. Runs against real Postgres.
 */
@SpringBootTest
@Testcontainers
@org.springframework.transaction.annotation.Transactional
class CatalogImporterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    /**
     * Stub fetcher: maps each URL to deterministic tiny bytes so content-addressed
     * storage keys are stable and distinct per URL. No network is hit (design §Testing).
     */
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
    CatalogImporter importer;

    @Autowired
    ProductRepository products;

    @Autowired
    ProductImageRepository images;

    @Autowired
    VariantRepository variants;

    @Autowired
    ProductTagRepository productTags;

    @Autowired
    CategoryRepository categories;

    // ---- fixtures ----

    private SourceCategory cat(long wooTermId, String slug, Long parentWooTermId) {
        return new SourceCategory(wooTermId, slug, "Name of " + slug, parentWooTermId,
                0, null, null, null);
    }

    private SourceProduct simpleProduct(long wooId, String slug, String name, String sku) {
        return new SourceProduct(wooId, slug, name, "simple", "publish",
                "short", "long", "", null, null,
                List.of(10L), List.of(),
                sku, 3700L, null, null, null, true, 5, 250,
                List.of(),
                List.of(new SourceImage(900L, "img-" + slug, "alt text", 0, true)), List.of());
    }

    private SourceCatalog catalogOf(List<SourceProduct> prods) {
        return catalogOf(prods, List.of(
                new SourceAttributeValue("dark", "Sötét", 0),
                new SourceAttributeValue("light", "Világos", 1)));
    }

    private SourceCatalog catalogOf(List<SourceProduct> prods, List<SourceAttributeValue> szinValues) {
        return new SourceCatalog(
                List.of(cat(10L, "festekek", null), cat(11L, "kretafestek", 10L)),
                List.of(new SourceAttribute(1L, "szin", "Szín", "select", szinValues)),
                prods, List.of());
    }

    // ---- tests ----

    @Test
    void freshImportCreatesProductWithDefaultVariantAndCategories() {
        ImportReport report = importer.run(catalogOf(List.of(
                simpleProduct(100L, "pure-white-chalk-paint", "Pure White", "ASFPW"))));

        assertThat(report.productsCreated()).isEqualTo(1);
        assertThat(report.errors()).isEmpty();

        Product p = products.findByExternalId(100L).orElseThrow();
        assertThat(p.getSlug()).isEqualTo("pure-white-chalk-paint");
        assertThat(p.getCategories()).hasSize(1);
        assertThat(p.getVariants()).hasSize(1);
        Variant v = p.getVariants().getFirst();
        assertThat(v.isDefault()).isTrue();
        assertThat(v.getSku()).isEqualTo("ASFPW");
        assertThat(v.regularPrice().amount()).isEqualTo(3700L);
    }

    @Test
    void reimportingSameCatalogCreatesNoDuplicates() {
        SourceCatalog catalog = catalogOf(List.of(
                simpleProduct(200L, "old-white-chalk-paint", "Old White", "ASFOW")));

        importer.run(catalog);
        long productId = products.findByExternalId(200L).orElseThrow().getId();
        long variantCount = variants.count();

        ImportReport second = importer.run(catalog);

        assertThat(second.productsCreated()).isZero();
        assertThat(products.findByExternalId(200L).orElseThrow().getId()).isEqualTo(productId);
        assertThat(variants.count()).isEqualTo(variantCount);
    }

    @Test
    void adminCreatedCategorySharingAWooSlugIsAdoptedNotDuplicated() {
        // An admin creates a category (external_id NULL) whose slug later shows up
        // in the Woo feed (catalogOf always imports slug "festekek" as wooTermId 10).
        // The import must adopt the existing row rather than insert a duplicate slug,
        // which would violate the UNIQUE constraint and abort the run (CLAUDE.md #4).
        Category manual = categories.save(Category.create(null, "festekek", "Kézi festékek"));
        assertThat(manual.getExternalId()).isNull();

        ImportReport report = importer.run(catalogOf(List.of(
                simpleProduct(600L, "graphite", "Graphite", "ASFGR"))));

        assertThat(report.errors()).isEmpty();
        assertThat(categories.findAll().stream().filter(c -> c.getSlug().equals("festekek")))
                .hasSize(1);
        Category adopted = categories.findBySlug("festekek").orElseThrow();
        assertThat(adopted.getExternalId()).isEqualTo(10L);
    }

    @Test
    void changedNameArrivesOnReimportButSlugIsPreserved() {
        importer.run(catalogOf(List.of(
                simpleProduct(300L, "coco-chalk-paint", "Coco", "ASFCO"))));

        SourceProduct renamed = simpleProduct(300L, "coco-chalk-paint-NEW-SLUG", "Coco RENAMED", "ASFCO");
        importer.run(catalogOf(List.of(renamed)));

        Product p = products.findByExternalId(300L).orElseThrow();
        assertThat(p.getName()).isEqualTo("Coco RENAMED");
        // CLAUDE.md #7: slugs are immutable once created
        assertThat(p.getSlug()).isEqualTo("coco-chalk-paint");
    }

    @Test
    void newProductInSourceIsCreatedOnReimport() {
        importer.run(catalogOf(List.of(
                simpleProduct(400L, "paris-grey", "Paris Grey", "ASFPG"))));

        ImportReport second = importer.run(catalogOf(List.of(
                simpleProduct(400L, "paris-grey", "Paris Grey", "ASFPG"),
                simpleProduct(401L, "paloma", "Paloma", "ASFPA"))));

        assertThat(second.productsCreated()).isEqualTo(1);
        assertThat(products.findByExternalId(401L)).isPresent();
    }

    @Test
    void variableProductGetsOneVariantPerVariationWithAttributeValues() {
        SourceVariant v1 = new SourceVariant(501L, "WAX-DARK-S", 4500L, null, null, null,
                true, 3, 120, 0, Map.of("szin", "dark"));
        SourceVariant v2 = new SourceVariant(502L, "WAX-DARK-L", 7900L, null, null, null,
                true, 2, 480, 1, Map.of("szin", "light"));
        SourceProduct variable = new SourceProduct(500L, "soft-wax", "Soft Wax", "variable",
                "publish", "s", "d", "", null, null,
                List.of(10L), List.of("szin"),
                null, null, null, null, null, true, 0, null,
                List.of(v1, v2), List.of(), List.of());

        importer.run(catalogOf(List.of(variable)));

        Product p = products.findByExternalId(500L).orElseThrow();
        assertThat(p.getVariants()).hasSize(2);
        Variant first = p.getVariants().getFirst();
        assertThat(first.getSku()).isEqualTo("WAX-DARK-S");
        assertThat(first.isDefault()).isFalse();
        assertThat(first.getAttributeValues()).hasSize(1);
        assertThat(first.getAttributeValues().iterator().next().getSlug()).isEqualTo("dark");
    }

    @Test
    void attributeValueLabelsComeFromSourceAndUpdateOnReimport() {
        SourceVariant sv = new SourceVariant(701L, "LBL-1", 1000L, null, null, null,
                true, 1, null, 0, Map.of("szin", "dark"));
        SourceProduct variable = new SourceProduct(700L, "label-check", "Label Check", "variable",
                "publish", null, null, null, null, null,
                List.of(10L), List.of("szin"),
                null, null, null, null, null, true, 0, null,
                List.of(sv), List.of(), List.of());

        importer.run(catalogOf(List.of(variable)));
        Variant v = products.findByExternalId(700L).orElseThrow().getVariants().getFirst();
        assertThat(v.getAttributeValues().iterator().next().getLabel()).isEqualTo("Sötét");

        importer.run(catalogOf(List.of(variable), List.of(
                new SourceAttributeValue("dark", "Sötétbarna", 0))));
        Variant after = products.findByExternalId(700L).orElseThrow().getVariants().getFirst();
        assertThat(after.getAttributeValues().iterator().next().getLabel()).isEqualTo("Sötétbarna");
    }

    @Test
    void categoryParentLinkageIsResolved() {
        importer.run(catalogOf(List.of(
                simpleProduct(600L, "linkage-check", "Linkage", "LNK"))));

        Product p = products.findByExternalId(600L).orElseThrow();
        // product is in category woo 10 ("festekek"); woo 11 is its child
        assertThat(p.getCategories().iterator().next().getParent()).isNull();
    }

    @Test
    void galleryImagesAreDownloadedIntoContentAddressedStorage() {
        // Source images carry the legacy wp/ key; the importer must download the bytes
        // and store them content-addressed (up/…), like the blog and workshop imports,
        // instead of persisting the hot-link key.
        SourceProduct p = new SourceProduct(900L, "downloaded-gallery", "Downloaded Gallery", "simple",
                "publish", "short", "long", "", null, null,
                List.of(10L), List.of(),
                "DLG01", 3700L, null, null, null, true, 5, 250,
                List.of(),
                List.of(new SourceImage(950L, "wp/2024/01/main.jpg", "alt", 0, true)),
                List.of());

        ImportReport report = importer.run(catalogOf(List.of(p)));

        assertThat(report.errors()).isEmpty();
        Product product = products.findByExternalId(900L).orElseThrow();
        List<ProductImage> gallery = images.findByProductOrderByPositionAsc(product);
        assertThat(gallery).hasSize(1);
        assertThat(gallery.getFirst().getStorageKey())
                .describedAs("gallery image must be self-hosted (up/<hash>), not the wp/ hot-link key")
                .startsWith("up/");
    }

    @Test
    void descriptionInlineImagesAreRewrittenToLocalMedia() {
        // Inline <img> tags in the product description point at the live WP uploads;
        // the importer must download them into storage and rewrite src to /media/… .
        String description = "<p>Leírás</p>"
                + "<img src=\"https://nemiskacat.hu/wp-content/uploads/2024/01/inline.jpg\" alt=\"kép\">";
        SourceProduct p = new SourceProduct(901L, "inline-desc", "Inline Desc", "simple",
                "publish", "short", description, "", null, null,
                List.of(10L), List.of(),
                "IDS01", 3700L, null, null, null, true, 5, 250,
                List.of(), List.of(), List.of());

        ImportReport report = importer.run(catalogOf(List.of(p)));

        assertThat(report.errors()).isEmpty();
        Product product = products.findByExternalId(901L).orElseThrow();
        assertThat(product.getDescription())
                .contains("/media/up/")
                .doesNotContain("wp-content/uploads");
    }

    @Test
    void tagIsUpsertedAndLinkedToProductIdempotently() {
        SourceTag tag = new SourceTag(42L, "vintage", "Vintage");
        SourceProduct taggedProduct = new SourceProduct(
                800L, "vintage-chalk-paint", "Vintage Chalk Paint", "simple", "publish",
                "short", "long", "", null, null,
                List.of(10L), List.of(),
                "VNTG01", 4200L, null, null, null, true, 3, 200,
                List.of(),
                List.of(new SourceImage(901L, "img-vintage", "alt text", 0, true)),
                List.of(42L));
        SourceCatalog catalog = new SourceCatalog(
                List.of(cat(10L, "festekek", null), cat(11L, "kretafestek", 10L)),
                List.of(new SourceAttribute(1L, "szin", "Szín", "select",
                        List.of(new SourceAttributeValue("dark", "Sötét", 0),
                                new SourceAttributeValue("light", "Világos", 1)))),
                List.of(taggedProduct),
                List.of(tag));

        importer.run(catalog);
        importer.run(catalog); // idempotent

        Product p = products.findByExternalId(800L).orElseThrow();
        assertThat(p.getTags()).extracting(ProductTag::getSlug).containsExactly("vintage");
        assertThat(productTags.findAll()).hasSize(1);
    }
}
