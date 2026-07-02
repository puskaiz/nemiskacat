package hu.deposoft.webshop.application.workshop;

import hu.deposoft.webshop.application.catalog.ImageFetcher;
import hu.deposoft.webshop.application.catalog.StorageService;
import hu.deposoft.webshop.application.content.InlineImageRewriter;
import hu.deposoft.webshop.domain.catalog.FulfilmentType;
import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductImage;
import hu.deposoft.webshop.domain.catalog.ProductImageRepository;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.ProductStatus;
import hu.deposoft.webshop.domain.catalog.ProductType;
import hu.deposoft.webshop.integrations.woo.SourceWorkshop;
import hu.deposoft.webshop.integrations.woo.SourceWorkshopImage;
import hu.deposoft.webshop.integrations.woo.SourceWorkshops;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent workshop import: upserts each workshop (Product type=WORKSHOP) by its
 * external (WP page) id, downloads and stores its slider/gallery images through the
 * shared product-image storage, and upserts the {@code product_image} rows. Mirrors
 * {@code CatalogImporter}'s structure and idempotency (CLAUDE.md #1, #4).
 *
 * <p>Re-running picks up name/description changes but never changes the slug
 * (CLAUDE.md #7) and never creates sessions/variants (those are entered in admin).
 * Image idempotency is by {@code (product, storageKey)}; storage keys are
 * content-addressed, so re-fetching identical bytes yields the same key.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkshopImporter {

    /** Woo tickets were standard-rate taxable; editable in admin afterwards (design §Stage 2). */
    private static final int WORKSHOP_VAT_PERCENT = 27;

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final StorageService storage;
    private final ImageFetcher imageFetcher;
    private final InlineImageRewriter inlineImageRewriter;

    @Transactional
    public WorkshopImportReport run(SourceWorkshops source) {
        WorkshopImportReport report = new WorkshopImportReport();
        for (SourceWorkshop workshop : source.workshops()) {
            try {
                upsertWorkshop(workshop, report);
            } catch (RuntimeException e) {
                report.error("workshop external_id=%d slug=%s: %s"
                        .formatted(workshop.externalId(), workshop.slug(), e.getMessage()));
                log.warn("Import failed for workshop external_id={}", workshop.externalId(), e);
            }
        }
        log.info("Workshop import finished: {}", report);
        return report;
    }

    private void upsertWorkshop(SourceWorkshop source, WorkshopImportReport report) {
        // Upsert by externalId (the WP page id); fall back to slug for any pre-existing row
        // that lacks an externalId (then lock it by setting the externalId).
        Product product = products.findByExternalId(source.externalId())
                .or(() -> products.findBySlug(source.slug()))
                .orElse(null);
        boolean created = product == null;
        if (created) {
            product = Product.create(source.externalId(), source.slug(), source.name(),
                    ProductType.WORKSHOP, ProductStatus.PUBLISHED);
            product.setInvoiceSource(InvoiceSource.BILLINGO);
            product.setFulfilmentType(FulfilmentType.EVENT);
            product.setVatRatePercent(WORKSHOP_VAT_PERCENT);
            product = products.save(product);
            report.workshopCreated();
        } else {
            if (!product.getSlug().equals(source.slug())) {
                log.warn("Slug change ignored for workshop external_id={} (ours='{}', source='{}') — slugs are immutable",
                        source.externalId(), product.getSlug(), source.slug());
            }
            report.workshopUpdated();
        }
        // On update set name + description; never the slug, never sessions/variants.
        product.setName(source.name());
        // Inline <img> widgets in the description hot-link the source site; download them
        // into local storage and rewrite src to /media/… like the blog import (CLAUDE.md #8).
        InlineImageRewriter.Result description = inlineImageRewriter.rewrite(source.descriptionHtml());
        product.setDescription(description.html());
        description.errors().forEach(e -> report.error(
                "workshop external_id=%d description image: %s".formatted(source.externalId(), e)));

        upsertGallery(product, source, report);
    }

    private void upsertGallery(Product product, SourceWorkshop source, WorkshopImportReport report) {
        for (SourceWorkshopImage image : source.images()) {
            try {
                ImageFetcher.FetchedImage fetched = imageFetcher.fetch(image.url());
                String storageKey = storage.put(fetched.bytes(), fetched.contentType());
                ProductImage existing = images.findByProductAndStorageKey(product, storageKey).orElse(null);
                if (existing == null) {
                    boolean featured = image.position() == 0;
                    images.save(ProductImage.create(product, storageKey, "", image.position(), featured));
                    report.imageCreated();
                } else {
                    existing.setPosition(image.position());
                    existing.setFeatured(image.position() == 0);
                }
            } catch (RuntimeException e) {
                report.error("workshop external_id=%d image url=%s: %s"
                        .formatted(source.externalId(), image.url(), e.getMessage()));
                log.warn("Image import failed for workshop external_id={} url={}",
                        source.externalId(), image.url(), e);
            }
        }
    }
}
