package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException;
import hu.deposoft.webshop.config.AdminProperties;
import hu.deposoft.webshop.domain.catalog.Category;
import hu.deposoft.webshop.domain.catalog.CategoryRepository;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.ProductStatus;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Flag-gated product content edits (P2a) — webshop fields only; writes to our DB, never Woo. */
@Service
@RequiredArgsConstructor
public class ProductAdminEditService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductAdminQueryService query;
    private final AdminProperties adminProperties;

    /** The product editor is disabled by the feature flag. */
    public static class EditorDisabledException extends RuntimeException {
        public EditorDisabledException(String message) { super(message); }
    }

    public record ContentUpdate(String name, String shortDescription, String description,
                                String seoTitle, String metaDescription, ProductStatus status,
                                List<String> categorySlugs) {}

    @Transactional
    public ProductAdminQueryService.ProductDetailView updateContent(Long id, ContentUpdate cmd) {
        if (!adminProperties.productEditorEnabled()) {
            throw new EditorDisabledException("Product editor is disabled");
        }
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("Product name is required");
        }
        if (cmd.status() == null) {
            throw new IllegalArgumentException("Product status is required");
        }
        Product p = products.findById(id).orElseThrow(() -> new NotFoundException("No product " + id));
        p.setName(cmd.name().trim());
        p.setShortDescription(cmd.shortDescription());
        p.setDescription(cmd.description());
        p.setSeoTitle(cmd.seoTitle());
        p.setMetaDescription(cmd.metaDescription());
        p.setStatus(cmd.status());
        // slug intentionally not editable (Woo 1:1, CLAUDE.md)
        Set<Category> resolved = new LinkedHashSet<>();
        for (String slug : (cmd.categorySlugs() == null ? List.<String>of() : cmd.categorySlugs())) {
            resolved.add(categories.findBySlug(slug)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown category: " + slug)));
        }
        p.getCategories().clear();
        p.getCategories().addAll(resolved);
        products.save(p);
        return query.detail(id);
    }
}
