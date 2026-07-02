package hu.deposoft.webshop.application.blog;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.application.catalog.CatalogQueryService;
import hu.deposoft.webshop.application.catalog.StorageService;
import hu.deposoft.webshop.domain.blog.BlogCategory;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.blog.BlogPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class BlogAdminService {

    private final BlogPostRepository posts;
    private final BlogCategoryRepository categories;
    private final BlogHtmlSanitizer sanitizer;
    private final CatalogQueryService catalog;
    private final StorageService storage;
    private final AuditService audit;

    public record PostUpsert(String slug, String title, String excerpt, String bodyHtml,
                             String seoTitle, String seoDescription,
                             List<String> categorySlugs, List<String> recommendedSkus) {}

    public record CategoryUpsert(String name, String slug) {}

    public record PostSummary(Long id, String slug, String title, String status,
                              OffsetDateTime publishedAt, OffsetDateTime updatedAt) {}

    public record PostDetail(Long id, String slug, String title, String excerpt,
                             String bodyHtml, String coverImageUrl, String status,
                             String seoTitle, String seoDescription,
                             List<String> categorySlugs, List<String> recommendedSkus) {}

    public record CategoryView(Long id, String name, String slug) {}

    public static class SlugConflictException extends RuntimeException {
        public SlugConflictException(String m) { super(m); }
    }
    public static class UnknownSkuException extends RuntimeException {
        public UnknownSkuException(String m) { super(m); }
    }
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    @Transactional(readOnly = true)
    public Page<PostSummary> list(int page, int size) {
        return posts.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .map(p -> new PostSummary(p.getId(), p.getSlug(), p.getTitle(),
                        p.getStatus().name(), p.getPublishedAt(), p.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public PostDetail get(Long id) {
        return toDetail(find(id));
    }

    public PostDetail create(PostUpsert cmd) {
        if (posts.existsBySlug(cmd.slug())) {
            throw new SlugConflictException("Slug already exists: " + cmd.slug());
        }
        BlogPost p = BlogPost.create(cmd.slug(), cmd.title());
        applyCreate(p, cmd);
        posts.save(p);
        audit.record("BLOG_POST_CREATE", "blog_post", String.valueOf(p.getId()), cmd.slug());
        return toDetail(p);
    }

    public PostDetail update(Long id, PostUpsert cmd) {
        BlogPost p = find(id);
        if (!p.getSlug().equals(cmd.slug()) && posts.existsBySlug(cmd.slug())) {
            throw new SlugConflictException("Slug already exists: " + cmd.slug());
        }
        // slug is immutable on the entity by design; updates keep the original slug.
        applyUpdate(p, cmd);
        audit.record("BLOG_POST_UPDATE", "blog_post", String.valueOf(id), p.getSlug());
        return toDetail(p);
    }

    public void delete(Long id) {
        BlogPost p = find(id);
        posts.delete(p);
        audit.record("BLOG_POST_DELETE", "blog_post", String.valueOf(id), p.getSlug());
    }

    public PostDetail publish(Long id) {
        BlogPost p = find(id);
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        audit.record("BLOG_POST_PUBLISH", "blog_post", String.valueOf(id), p.getSlug());
        return toDetail(p);
    }

    public PostDetail unpublish(Long id) {
        BlogPost p = find(id);
        p.unpublish();
        audit.record("BLOG_POST_UNPUBLISH", "blog_post", String.valueOf(id), p.getSlug());
        return toDetail(p);
    }

    public PostDetail uploadCover(Long id, byte[] bytes, String contentType) {
        BlogPost p = find(id);
        String key = storage.put(bytes, contentType);
        p.setCoverImageKey(key);
        audit.record("BLOG_POST_COVER", "blog_post", String.valueOf(id), key);
        return toDetail(p);
    }

    @Transactional(readOnly = true)
    public CategoryView getCategory(Long id) {
        BlogCategory c = categories.findById(id)
                .orElseThrow(() -> new NotFoundException("Blog category not found: " + id));
        return new CategoryView(c.getId(), c.getName(), c.getSlug());
    }

    @Transactional(readOnly = true)
    public List<CategoryView> listCategories() {
        return categories.findAllByOrderByNameAsc().stream()
                .map(c -> new CategoryView(c.getId(), c.getName(), c.getSlug()))
                .toList();
    }

    public CategoryView createCategory(CategoryUpsert cmd) {
        if (categories.existsBySlug(cmd.slug())) {
            throw new SlugConflictException("Category slug already exists: " + cmd.slug());
        }
        BlogCategory c = categories.save(BlogCategory.create(cmd.name(), cmd.slug()));
        audit.record("BLOG_CATEGORY_CREATE", "blog_category", String.valueOf(c.getId()), cmd.slug());
        return new CategoryView(c.getId(), c.getName(), c.getSlug());
    }

    public CategoryView updateCategory(Long id, CategoryUpsert cmd) {
        BlogCategory c = categories.findById(id)
                .orElseThrow(() -> new NotFoundException("Blog category not found: " + id));
        // Category slugs are immutable by design (CLAUDE.md #7 — URL slugs must never change).
        // BlogCategory has no slug setter, so cmd.slug() is intentionally not applied here;
        // only the display name is editable.
        c.setName(cmd.name());
        audit.record("BLOG_CATEGORY_UPDATE", "blog_category", String.valueOf(id), c.getSlug());
        return new CategoryView(c.getId(), c.getName(), c.getSlug());
    }

    public void deleteCategory(Long id) {
        BlogCategory c = categories.findById(id)
                .orElseThrow(() -> new NotFoundException("Blog category not found: " + id));
        categories.delete(c);
        audit.record("BLOG_CATEGORY_DELETE", "blog_category", String.valueOf(id), c.getSlug());
    }

    private BlogPost find(Long id) {
        return posts.findById(id)
                .orElseThrow(() -> new NotFoundException("Blog post not found: " + id));
    }

    /**
     * Apply cmd to a newly constructed (unmanaged) BlogPost on create.
     * Using setters directly is safe here — no managed collection reference exists yet.
     */
    private void applyCreate(BlogPost p, PostUpsert cmd) {
        p.setTitle(cmd.title());
        p.setExcerpt(cmd.excerpt());
        p.setBodyHtml(sanitizer.sanitize(cmd.bodyHtml() == null ? "" : cmd.bodyHtml()));
        p.setSeoTitle(cmd.seoTitle());
        p.setSeoDescription(cmd.seoDescription());
        p.setCategories(resolveCategories(cmd.categorySlugs()));
        p.setRecommendedSkus(validateSkus(cmd.recommendedSkus()));
    }

    /**
     * Apply cmd to an already-managed BlogPost on update.
     * For @ManyToMany categories: mutate in place (clear + addAll) to preserve Hibernate's
     * managed collection reference and avoid dirty-tracking issues.
     * For @ElementCollection recommendedSkus: setRecommendedSkus replaces the backing list,
     * which is safe for element collections — Hibernate re-inserts them from scratch on flush.
     */
    private void applyUpdate(BlogPost p, PostUpsert cmd) {
        p.setTitle(cmd.title());
        p.setExcerpt(cmd.excerpt());
        p.setBodyHtml(sanitizer.sanitize(cmd.bodyHtml() == null ? "" : cmd.bodyHtml()));
        p.setSeoTitle(cmd.seoTitle());
        p.setSeoDescription(cmd.seoDescription());
        // Mutate the managed Set in place to keep Hibernate dirty-tracking correct
        p.getCategories().clear();
        p.getCategories().addAll(resolveCategories(cmd.categorySlugs()));
        p.setRecommendedSkus(validateSkus(cmd.recommendedSkus()));
    }

    private Set<BlogCategory> resolveCategories(List<String> slugs) {
        Set<BlogCategory> result = new LinkedHashSet<>();
        if (slugs == null) return result;
        for (String slug : slugs) {
            categories.findBySlug(slug).ifPresent(result::add);
        }
        return result;
    }

    private List<String> validateSkus(List<String> skus) {
        if (skus == null) return List.of();
        for (String sku : skus) {
            if (!catalog.skuExists(sku)) {
                throw new UnknownSkuException("Unknown product SKU: " + sku);
            }
        }
        return skus;
    }

    private PostDetail toDetail(BlogPost p) {
        return new PostDetail(p.getId(), p.getSlug(), p.getTitle(), p.getExcerpt(),
                p.getBodyHtml(),
                p.getCoverImageKey() == null ? null : "/media/" + p.getCoverImageKey(),
                p.getStatus().name(), p.getSeoTitle(), p.getSeoDescription(),
                p.getCategories().stream().map(BlogCategory::getSlug).toList(),
                // Materialize the lazy @ElementCollection inside the transaction: with
                // spring.jpa.open-in-view=false the session is closed before Jackson serializes
                // the response, so a bare lazy reference would throw LazyInitializationException.
                List.copyOf(p.getRecommendedSkus()));
    }
}
