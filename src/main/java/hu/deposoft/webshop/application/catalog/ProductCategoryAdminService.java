package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.catalog.Category;
import hu.deposoft.webshop.domain.catalog.CategoryRepository;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin CRUD for product categories, mirroring {@link ProductTagAdminService}.
 *
 * <p>Woo-import boundary: admin-created categories carry {@code externalId == null} and are never
 * touched by the WooCommerce importer. A rename of a Woo-imported category (externalId set) is
 * re-synced from Woo on the next import — the importer overwrites {@code name} keyed by externalId.
 * This is the intended "Woo is source of truth" behavior (CLAUDE.md #4); we deliberately do NOT
 * try to protect admin renames of imported categories from being overwritten by reimport.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProductCategoryAdminService {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final AuditService audit;

    public record CategoryView(Long id, String name, String slug) {}
    public record CategoryUpsert(String name, String slug) {}
    public record RenameRequest(String name) {}

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    @Transactional(readOnly = true)
    public CategoryView getById(Long id) {
        Category c = find(id);
        return new CategoryView(c.getId(), c.getName(), c.getSlug());
    }

    @Transactional(readOnly = true)
    public List<CategoryView> list() {
        return categories.findAllByOrderByNameAsc().stream()
                .map(c -> new CategoryView(c.getId(), c.getName(), c.getSlug())).toList();
    }

    public CategoryView create(CategoryUpsert cmd) {
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (!BlogPost.isValidSlug(cmd.slug())) {
            throw new IllegalArgumentException("Invalid product category slug: " + cmd.slug());
        }
        if (categories.existsBySlug(cmd.slug())) {
            throw new IllegalArgumentException("Product category slug already exists: " + cmd.slug());
        }
        // externalId null marks it admin-created (never touched by the importer).
        Category c = categories.save(Category.create(null, cmd.slug(), cmd.name().trim()));
        audit.record("PRODUCT_CATEGORY_CREATE", "category", String.valueOf(c.getId()), c.getSlug());
        return new CategoryView(c.getId(), c.getName(), c.getSlug());
    }

    public CategoryView rename(Long id, RenameRequest cmd) {
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Category c = find(id);
        c.setName(cmd.name().trim());   // slug immutable (CLAUDE.md #7)
        audit.record("PRODUCT_CATEGORY_UPDATE", "category", String.valueOf(id), c.getSlug());
        return new CategoryView(c.getId(), c.getName(), c.getSlug());
    }

    public void delete(Long id) {
        Category c = find(id);
        // Product owns the product_category join (Product.categories, @ManyToMany + @JoinTable);
        // Category has no back-reference, so a plain delete of a referenced category would violate
        // the FK. Detach it from every referencing product first so the delete succeeds.
        for (Product p : products.findByCategories_Id(id)) {
            p.getCategories().remove(c);
        }
        categories.delete(c);
        audit.record("PRODUCT_CATEGORY_DELETE", "category", String.valueOf(id), c.getSlug());
    }

    private Category find(Long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Product category not found: " + id));
    }
}
