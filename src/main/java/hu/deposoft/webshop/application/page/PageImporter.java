package hu.deposoft.webshop.application.page;

import hu.deposoft.webshop.application.blog.BlogHtmlSanitizer;
import hu.deposoft.webshop.application.content.InlineImageRewriter;
import hu.deposoft.webshop.domain.page.ContentPage;
import hu.deposoft.webshop.domain.page.ContentPageRepository;
import hu.deposoft.webshop.integrations.wordpress.SourcePage;
import hu.deposoft.webshop.integrations.wordpress.SourcePages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Idempotent WordPress content-page import: upserts by external id (WP page id),
 * falling back to slug (CLAUDE.md #4); never mutates the slug (CLAUDE.md #7);
 * re-hosts inline upload images (CLAUDE.md #8) and sanitizes the HTML (CLAUDE.md #9).
 * Mirrors {@code BlogImporter}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PageImporter {

    private final ContentPageRepository pages;
    private final BlogHtmlSanitizer sanitizer;
    private final InlineImageRewriter inlineImageRewriter;

    @Transactional
    public PageImportReport run(SourcePages source) {
        PageImportReport report = new PageImportReport();
        List<SourcePage> list = source.pages() == null ? List.of() : source.pages();
        for (SourcePage p : list) {
            try {
                upsert(p, report);
            } catch (RuntimeException e) {
                report.error("page slug=%s: %s".formatted(p.slug(), e.getMessage()));
                log.warn("Page import failed for slug={}", p.slug(), e);
            }
        }
        log.info("Page import finished: {}", report);
        return report;
    }

    private void upsert(SourcePage src, PageImportReport report) {
        if (src.slug() == null || src.slug().isBlank()) {
            report.skipped();
            report.error("page externalId=%d has no slug — skipped".formatted(src.externalId()));
            return;
        }
        InlineImageRewriter.Result rewritten = inlineImageRewriter.rewrite(src.bodyHtml());
        for (int i = 0; i < rewritten.stored(); i++) {
            report.imageStored();
        }
        rewritten.errors().forEach(report::error);
        String bodyHtml = sanitizer.sanitize(rewritten.html());

        ContentPage page = pages.findByExternalId(src.externalId())
                .or(() -> pages.findBySlug(src.slug()))
                .orElse(null);
        boolean created = page == null;
        if (created) {
            page = ContentPage.create(src.externalId(), src.slug(), src.title());
        } else {
            page.setTitle(src.title());   // slug intentionally left unchanged (immutable)
        }
        page.setBodyHtml(bodyHtml);
        page.setSeoTitle(src.seoTitle());
        page.setSeoDescription(src.seoDescription());

        if ("publish".equalsIgnoreCase(src.status())) {
            page.publish(src.publishedAt());
        } else {
            page.unpublish();
        }

        if (created) {
            pages.save(page);
            report.created();
        } else {
            report.updated();
        }
    }
}
