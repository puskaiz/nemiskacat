package hu.deposoft.webshop.application.page;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.application.blog.BlogHtmlSanitizer;
import hu.deposoft.webshop.domain.page.ContentPage;
import hu.deposoft.webshop.domain.page.ContentPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Transactional
public class PageAdminService {

    private final ContentPageRepository pages;
    private final BlogHtmlSanitizer sanitizer;
    private final AuditService audit;

    public record PageUpsert(String slug, String title, String bodyHtml,
                             String seoTitle, String seoDescription) {}

    public record PageSummary(Long id, String slug, String title, String status,
                              OffsetDateTime publishedAt, OffsetDateTime updatedAt) {}

    public record PageDetail(Long id, String slug, String title, String bodyHtml,
                             String status, String seoTitle, String seoDescription) {}

    public static class SlugConflictException extends RuntimeException {
        public SlugConflictException(String m) { super(m); }
    }
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    @Transactional(readOnly = true)
    public Page<PageSummary> list(int page, int size) {
        return pages.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")))
                .map(p -> new PageSummary(p.getId(), p.getSlug(), p.getTitle(),
                        p.getStatus().name(), p.getPublishedAt(), p.getUpdatedAt()));
    }

    @Transactional(readOnly = true)
    public PageDetail get(Long id) {
        return toDetail(find(id));
    }

    public PageDetail create(PageUpsert cmd) {
        if (pages.existsBySlug(cmd.slug())) {
            throw new SlugConflictException("Slug already exists: " + cmd.slug());
        }
        ContentPage p = ContentPage.create(null, cmd.slug(), cmd.title());
        apply(p, cmd);
        pages.save(p);
        audit.record("CONTENT_PAGE_CREATE", "content_page", String.valueOf(p.getId()), cmd.slug());
        return toDetail(p);
    }

    public PageDetail update(Long id, PageUpsert cmd) {
        ContentPage p = find(id);
        if (!p.getSlug().equals(cmd.slug()) && pages.existsBySlug(cmd.slug())) {
            throw new SlugConflictException("Slug already exists: " + cmd.slug());
        }
        // slug is immutable on the entity by design; updates keep the original slug.
        apply(p, cmd);
        audit.record("CONTENT_PAGE_UPDATE", "content_page", String.valueOf(id), p.getSlug());
        return toDetail(p);
    }

    public void delete(Long id) {
        ContentPage p = find(id);
        pages.delete(p);
        audit.record("CONTENT_PAGE_DELETE", "content_page", String.valueOf(id), p.getSlug());
    }

    public PageDetail publish(Long id) {
        ContentPage p = find(id);
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        audit.record("CONTENT_PAGE_PUBLISH", "content_page", String.valueOf(id), p.getSlug());
        return toDetail(p);
    }

    public PageDetail unpublish(Long id) {
        ContentPage p = find(id);
        p.unpublish();
        audit.record("CONTENT_PAGE_UNPUBLISH", "content_page", String.valueOf(id), p.getSlug());
        return toDetail(p);
    }

    private ContentPage find(Long id) {
        return pages.findById(id)
                .orElseThrow(() -> new NotFoundException("Content page not found: " + id));
    }

    private void apply(ContentPage p, PageUpsert cmd) {
        p.setTitle(cmd.title());
        p.setBodyHtml(sanitizer.sanitize(cmd.bodyHtml() == null ? "" : cmd.bodyHtml()));
        p.setSeoTitle(cmd.seoTitle());
        p.setSeoDescription(cmd.seoDescription());
    }

    private PageDetail toDetail(ContentPage p) {
        return new PageDetail(p.getId(), p.getSlug(), p.getTitle(), p.getBodyHtml(),
                p.getStatus().name(), p.getSeoTitle(), p.getSeoDescription());
    }
}
