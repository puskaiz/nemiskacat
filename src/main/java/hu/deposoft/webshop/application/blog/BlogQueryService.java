package hu.deposoft.webshop.application.blog;

import hu.deposoft.webshop.application.catalog.CatalogQueryService;
import hu.deposoft.webshop.application.catalog.CatalogQueryService.CategoryItemView;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.blog.BlogPostRepository;
import hu.deposoft.webshop.domain.blog.PublicationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlogQueryService {

    public static final int PAGE_SIZE = 10;
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Europe/Budapest");

    private final BlogPostRepository posts;
    private final BlogCategoryRepository categories;
    private final CatalogQueryService catalog;
    private final ObjectMapper objectMapper;
    private final ExcerptDeriver excerptDeriver;

    public record CategoryRef(String name, String slug) {}

    public record BlogListItem(String slug, String title, String excerpt,
                               String coverImageUrl, LocalDate publishedDate,
                               List<CategoryRef> categories) {}

    public record BlogListView(List<BlogListItem> items, int page, int totalPages,
                               String categoryName, String categorySlug) {}

    public record BlogPostView(String slug, String title, String bodyHtml,
                               String coverImageUrl, OffsetDateTime publishedAt,
                               LocalDate publishedDate, String seoTitle, String seoDescription,
                               List<CategoryRef> categories,
                               List<CategoryItemView> recommendedProducts,
                               String jsonLd) {}

    public BlogListView publishedList(int page) {
        Page<BlogPost> result = posts.findByStatus(PublicationStatus.PUBLISHED, pageable(page));
        return toListView(result, page, null, null);
    }

    public Optional<BlogListView> publishedListByCategory(String categorySlug, int page) {
        return categories.findBySlug(categorySlug).map(cat -> {
            Page<BlogPost> result = posts.findByStatusAndCategories_Slug(
                    PublicationStatus.PUBLISHED, categorySlug, pageable(page));
            return toListView(result, page, cat.getName(), cat.getSlug());
        });
    }

    public Optional<BlogPostView> getPublishedBySlug(String slug) {
        return posts.findBySlug(slug)
                .filter(p -> p.getStatus() == PublicationStatus.PUBLISHED)
                .map(this::toPostView);
    }

    private PageRequest pageable(int page) {
        return PageRequest.of(Math.max(0, page - 1), PAGE_SIZE,
                Sort.by(Sort.Direction.DESC, "publishedAt"));
    }

    private BlogListView toListView(Page<BlogPost> result, int page, String catName, String catSlug) {
        List<BlogListItem> items = result.getContent().stream()
                .map(p -> new BlogListItem(p.getSlug(), p.getTitle(), effectiveExcerpt(p),
                        mediaUrl(p.getCoverImageKey()), publishedDate(p), categoryRefs(p)))
                .toList();
        return new BlogListView(items, page, Math.max(1, result.getTotalPages()), catName, catSlug);
    }

    private BlogPostView toPostView(BlogPost p) {
        String coverUrl = mediaUrl(p.getCoverImageKey());
        String resolvedDescription = p.getSeoDescription() != null ? p.getSeoDescription() : effectiveExcerpt(p);

        LinkedHashMap<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", "https://schema.org");
        ld.put("@type", "Article");
        ld.put("headline", p.getTitle());
        if (p.getPublishedAt() != null) {
            ld.put("datePublished", p.getPublishedAt().toString());
        }
        if (coverUrl != null) {
            ld.put("image", coverUrl);
        }
        if (resolvedDescription != null) {
            ld.put("description", resolvedDescription);
        }
        String jsonLd = objectMapper.writeValueAsString(ld);

        return new BlogPostView(p.getSlug(), p.getTitle(), p.getBodyHtml(),
                coverUrl, p.getPublishedAt(), publishedDate(p),
                p.getSeoTitle() != null ? p.getSeoTitle() : p.getTitle(),
                resolvedDescription,
                categoryRefs(p), catalog.cardsBySkus(p.getRecommendedSkus()),
                jsonLd);
    }

    private String effectiveExcerpt(BlogPost p) {
        if (p.getExcerpt() != null && !p.getExcerpt().isBlank()) {
            return p.getExcerpt();
        }
        String derived = excerptDeriver.deriveFromHtml(p.getBodyHtml());
        return derived.isBlank() ? null : derived;
    }

    private List<CategoryRef> categoryRefs(BlogPost p) {
        return p.getCategories().stream()
                .map(c -> new CategoryRef(c.getName(), c.getSlug()))
                .toList();
    }

    private LocalDate publishedDate(BlogPost p) {
        return p.getPublishedAt() == null ? null
                : p.getPublishedAt().atZoneSameInstant(DISPLAY_ZONE).toLocalDate();
    }

    private String mediaUrl(String key) {
        return key == null ? null : "/media/" + key;
    }
}
