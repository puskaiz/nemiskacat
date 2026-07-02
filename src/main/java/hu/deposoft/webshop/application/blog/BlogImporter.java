package hu.deposoft.webshop.application.blog;

import hu.deposoft.webshop.application.catalog.ImageFetcher;
import hu.deposoft.webshop.application.catalog.StorageService;
import hu.deposoft.webshop.application.content.InlineImageRewriter;
import hu.deposoft.webshop.domain.blog.BlogCategory;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.blog.BlogPostRepository;
import hu.deposoft.webshop.domain.blog.BlogTag;
import hu.deposoft.webshop.domain.blog.BlogTagRepository;
import hu.deposoft.webshop.integrations.wordpress.SourceBlog;
import hu.deposoft.webshop.integrations.wordpress.SourceBlogCategory;
import hu.deposoft.webshop.integrations.wordpress.SourceBlogPost;
import hu.deposoft.webshop.integrations.wordpress.SourceBlogTag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Idempotent WordPress blog import: upserts categories then posts by slug
 * (CLAUDE.md #4, #7), normalises post HTML, downloads cover + inline
 * {@code wp-content/uploads} images into content-addressed storage (CLAUDE.md #8),
 * rewrites their HTML {@code src} attributes to {@code /media/<key>}, then sanitizes
 * and stores as {@code body_html}. Mirrors {@code WorkshopImporter}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BlogImporter {

    private final BlogPostRepository posts;
    private final BlogCategoryRepository categories;
    private final BlogTagRepository tags;
    private final BlogHtmlTransform blogHtmlTransform;
    private final BlogHtmlSanitizer blogHtmlSanitizer;
    private final ImageFetcher imageFetcher;
    private final StorageService storage;
    private final InlineImageRewriter inlineImageRewriter;

    @Transactional
    public BlogImportReport run(SourceBlog source) {
        BlogImportReport report = new BlogImportReport();
        Map<String, BlogCategory> categoryBySlug = upsertCategories(source, report);
        Map<String, BlogTag> tagBySlug = upsertTags(source, report);
        for (SourceBlogPost post : nullToEmpty(source.posts())) {
            try {
                upsertPost(post, categoryBySlug, tagBySlug, report);
            } catch (RuntimeException e) {
                report.error("post slug=%s: %s".formatted(post.slug(), e.getMessage()));
                log.warn("Blog import failed for slug={}", post.slug(), e);
            }
        }
        log.info("Blog import finished: {}", report);
        return report;
    }

    private Map<String, BlogCategory> upsertCategories(SourceBlog source, BlogImportReport report) {
        Map<String, BlogCategory> bySlug = new LinkedHashMap<>();
        for (SourceBlogCategory c : nullToEmpty(source.categories())) {
            BlogCategory entity = categories.findBySlug(c.slug()).orElse(null);
            if (entity == null) {
                BlogCategory created = BlogCategory.create(c.name(), c.slug());
                if ("uncategorized".equals(c.slug())) {
                    created.setSidebarHidden(true);   // keep the catch-all out of the public sidebar
                }
                entity = categories.save(created);
                report.categoryCreated();
            }
            bySlug.put(c.slug(), entity);
        }
        return bySlug;
    }

    private Map<String, BlogTag> upsertTags(SourceBlog source, BlogImportReport report) {
        Map<String, BlogTag> bySlug = new LinkedHashMap<>();
        for (SourceBlogTag tsrc : nullToEmpty(source.tags())) {
            BlogTag entity = tags.findBySlug(tsrc.slug()).orElse(null);
            if (entity == null) {
                entity = tags.save(BlogTag.create(tsrc.name(), tsrc.slug()));
                report.tagCreated();
            }
            bySlug.put(tsrc.slug(), entity);
        }
        return bySlug;
    }

    private void upsertPost(SourceBlogPost source, Map<String, BlogCategory> categoryBySlug,
                            Map<String, BlogTag> tagBySlug, BlogImportReport report) {
        if (source.slug() == null || source.slug().isBlank()) {
            report.postSkipped();
            report.error("post externalId=%d has no slug — skipped".formatted(source.externalId()));
            return;
        }
        String html = blogHtmlTransform.normalize(source.contentHtml());
        InlineImageRewriter.Result rewritten = inlineImageRewriter.rewrite(html);
        html = rewritten.html();
        for (int i = 0; i < rewritten.stored(); i++) {
            report.imageStored();
        }
        rewritten.errors().forEach(report::error);
        String bodyHtml = blogHtmlSanitizer.sanitize(html);

        BlogPost post = posts.findBySlug(source.slug()).orElse(null);
        boolean created = post == null;
        if (created) {
            post = BlogPost.create(source.slug(), source.title());
        } else {
            post.setTitle(source.title());
        }
        post.setExcerpt(source.excerpt());
        post.setBodyHtml(bodyHtml);
        post.setSeoTitle(source.seoTitle());
        post.setSeoDescription(source.seoDescription());

        // categories: replace via in-place mutation on update (Hibernate dirty-tracking),
        // setter on create (entity not yet managed) — mirrors BlogAdminService.
        Set<BlogCategory> resolved = new LinkedHashSet<>();
        if (source.categorySlugs() != null) {
            for (String slug : source.categorySlugs()) {
                BlogCategory c = categoryBySlug.get(slug);
                if (c == null) {
                    c = categories.findBySlug(slug).orElse(null);
                }
                if (c != null) {
                    resolved.add(c);
                }
            }
        }
        if (created) {
            post.setCategories(resolved);
        } else {
            post.getCategories().clear();
            post.getCategories().addAll(resolved);
        }

        Set<BlogTag> resolvedTags = new LinkedHashSet<>();
        if (source.tagSlugs() != null) {
            for (String slug : source.tagSlugs()) {
                BlogTag tg = tagBySlug.get(slug);
                if (tg == null) tg = tags.findBySlug(slug).orElse(null);
                if (tg != null) resolvedTags.add(tg);
            }
        }
        if (created) {
            post.setTags(resolvedTags);
        } else {
            post.getTags().clear();
            post.getTags().addAll(resolvedTags);
        }

        if (source.coverImageUrl() != null && !source.coverImageUrl().isBlank()) {
            try {
                ImageFetcher.FetchedImage img = imageFetcher.fetch(source.coverImageUrl());
                post.setCoverImageKey(storage.put(img.bytes(), img.contentType()));
                report.imageStored();
            } catch (RuntimeException e) {
                report.error("cover %s: %s".formatted(source.coverImageUrl(), e.getMessage()));
                log.warn("Cover image fetch failed for slug={} url={}", source.slug(), source.coverImageUrl(), e);
            }
        }

        if ("publish".equalsIgnoreCase(source.status())) {
            post.publish(source.publishedAt());   // sets publishedAt only if null (re-import keeps original)
        } else {
            post.unpublish();
        }

        if (created) {
            posts.save(post);
            report.postCreated();
        } else {
            report.postUpdated();
        }
    }

    private static <T> java.util.List<T> nullToEmpty(java.util.List<T> list) {
        return list == null ? java.util.List.of() : list;
    }
}
