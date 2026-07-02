package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.catalog.ProductAdminEditService.EditorDisabledException;
import hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException;
import hu.deposoft.webshop.config.AdminProperties;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductImage;
import hu.deposoft.webshop.domain.catalog.ProductImageRepository;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageService {

    // Content-type is trusted from the (ADMIN-only, flag-gated, CSRF-protected) client because the
    // app does no image decoding — resizing is handled by CDN optimizer URL params (CLAUDE.md #8).
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp");

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final StorageService storage;
    private final ProductAdminQueryService query;
    private final AdminProperties adminProperties;

    @Transactional
    public ProductAdminQueryService.ProductDetailView upload(Long productId, byte[] bytes, String contentType, String filename) {
        if (contentType == null || !ALLOWED.contains(contentType)) {
            throw new IllegalArgumentException("Unsupported image type: " + contentType);
        }
        Product p = product(productId);
        guard(p);
        String key = storage.put(bytes, contentType);
        List<ProductImage> existing = images.findByProductOrderByPositionAsc(p);
        int position = existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getPosition() + 1;
        images.save(ProductImage.create(p, key, filename == null ? "" : filename, position, existing.isEmpty()));
        return query.detail(productId);
    }

    @Transactional
    public ProductAdminQueryService.ProductDetailView delete(Long productId, Long imageId) {
        Product p = product(productId);
        guard(p);
        ProductImage img = imageOf(p, imageId);
        String key = img.getStorageKey();
        images.delete(img);
        boolean orphaned = !images.existsByStorageKey(key); // exists-query auto-flushes the pending delete
        reindex(p);
        if (orphaned) {
            deleteBlobAfterCommit(key);
        }
        return query.detail(productId);
    }

    @Transactional
    public ProductAdminQueryService.ProductDetailView setCover(Long productId, Long imageId) {
        Product p = product(productId);
        guard(p);
        ProductImage cover = imageOf(p, imageId);
        List<ProductImage> ordered = images.findByProductOrderByPositionAsc(p);
        ordered.remove(cover);
        ordered.add(0, cover);
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setPosition(i);
            ordered.get(i).setFeatured(ordered.get(i) == cover);
        }
        return query.detail(productId);
    }

    @Transactional
    public ProductAdminQueryService.ProductDetailView reorder(Long productId, List<Long> imageIds) {
        Product p = product(productId);
        guard(p);
        List<ProductImage> current = images.findByProductOrderByPositionAsc(p);
        if (imageIds.size() != current.size()
                || !imageIds.stream().sorted().toList().equals(current.stream().map(ProductImage::getId).sorted().toList())) {
            throw new IllegalArgumentException("Reorder list must contain exactly the product's image ids");
        }
        for (int i = 0; i < imageIds.size(); i++) {
            Long id = imageIds.get(i);
            ProductImage img = current.stream().filter(x -> x.getId().equals(id)).findFirst().orElseThrow();
            img.setPosition(i);
            img.setFeatured(i == 0);
        }
        return query.detail(productId);
    }

    /**
     * The product-editor flag gates editing of imported catalog products until the Woo cutover
     * (ADR 0005). Workshops are hand-managed admin content (not edited in Woo), so their gallery
     * is always available regardless of the flag.
     */
    private void guard(Product p) {
        if (p.getType() == hu.deposoft.webshop.domain.catalog.ProductType.WORKSHOP) {
            return;
        }
        if (!adminProperties.productEditorEnabled()) {
            throw new EditorDisabledException("Product editor is disabled");
        }
    }

    private Product product(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("No product " + id));
    }

    private ProductImage imageOf(Product p, Long imageId) {
        return images.findByProductOrderByPositionAsc(p).stream()
                .filter(i -> i.getId().equals(imageId)).findFirst()
                .orElseThrow(() -> new NotFoundException("No image " + imageId + " on product " + p.getId()));
    }

    /** Rewrites positions 0..n and keeps the cover invariant: only the first image is featured. */
    private void reindex(Product p) {
        List<ProductImage> ordered = images.findByProductOrderByPositionAsc(p);
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).setPosition(i);
            ordered.get(i).setFeatured(i == 0);
        }
    }

    /** Delete the now-unreferenced original only after the metadata delete commits; a storage failure
     *  must not roll back the committed row (content-addressed blobs are safe to leave orphaned). */
    private void deleteBlobAfterCommit(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {
                    try {
                        storage.delete(key);
                    } catch (RuntimeException e) {
                        log.warn("Failed to delete orphaned image blob {}", key, e);
                    }
                }
            });
        } else {
            storage.delete(key);
        }
    }
}
