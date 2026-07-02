package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.content.InlineImageRewriter;
import hu.deposoft.webshop.config.WebshopProperties;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductImage;
import hu.deposoft.webshop.domain.catalog.ProductImageRepository;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * One-time backfill: pull already-imported catalog and workshop images that still
 * hot-link the source site into local content-addressed storage, exactly like the
 * blog (CLAUDE.md #8). Covers two cases the earlier imports left behind:
 * <ul>
 *   <li>gallery/featured images whose {@code product_image.storage_key} is still a
 *       legacy {@code wp/…} key — downloaded and repointed to {@code up/<hash>};</li>
 *   <li>inline {@code <img src="…/wp-content/uploads/…">} in product/workshop
 *       descriptions — downloaded and rewritten to {@code /media/…}.</li>
 * </ul>
 *
 * <p>Idempotent: gallery relocation only touches {@code wp/} keys (relocated rows become
 * {@code up/}), and {@link InlineImageRewriter} only rewrites {@code /wp-content/uploads/}
 * sources (already-{@code /media/} rows are skipped). Storage is content-addressed, so a
 * re-run downloads nothing new. Run with profile {@code backfill-images}, then exit.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImageBackfillService {

    private static final String LEGACY_KEY_PREFIX = "wp/";
    private static final String INLINE_MARKER = "/wp-content/uploads/";

    private final ProductImageRepository images;
    private final ProductRepository products;
    private final ImageFetcher imageFetcher;
    private final StorageService storage;
    private final WebshopProperties properties;
    private final InlineImageRewriter inlineImageRewriter;

    public record Report(int galleryRelocated, int descriptionsRewritten, List<String> errors) {}

    @Transactional
    public Report run() {
        List<String> errors = new ArrayList<>();
        int gallery = relocateGalleryImages(errors);
        int descriptions = rewriteDescriptions(errors);
        Report report = new Report(gallery, descriptions, errors);
        log.info("Image backfill: {} gallery images relocated, {} descriptions rewritten, {} errors",
                gallery, descriptions, errors.size());
        return report;
    }

    private int relocateGalleryImages(List<String> errors) {
        int n = 0;
        for (ProductImage image : images.findByStorageKeyStartingWith(LEGACY_KEY_PREFIX)) {
            String sourceUrl = properties.imageUrl(image.getStorageKey());
            try {
                ImageFetcher.FetchedImage fetched = imageFetcher.fetch(sourceUrl);
                String key = storage.put(fetched.bytes(), fetched.contentType());
                image.relocateStorage(key);
                n++;
            } catch (RuntimeException e) {
                errors.add("gallery image id=%d key=%s: %s"
                        .formatted(image.getId(), image.getStorageKey(), e.getMessage()));
                log.warn("Backfill failed for image id={} key={}", image.getId(), image.getStorageKey(), e);
            }
        }
        return n;
    }

    private int rewriteDescriptions(List<String> errors) {
        int n = 0;
        for (Product product : products.findAll()) {
            String description = product.getDescription();
            if (description == null || !description.contains(INLINE_MARKER)) {
                continue;
            }
            InlineImageRewriter.Result rewritten = inlineImageRewriter.rewrite(description);
            product.setDescription(rewritten.html());
            rewritten.errors().forEach(e -> errors.add(
                    "product id=%d description image: %s".formatted(product.getId(), e)));
            n++;
        }
        return n;
    }
}
