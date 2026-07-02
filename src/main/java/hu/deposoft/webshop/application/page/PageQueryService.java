package hu.deposoft.webshop.application.page;

import hu.deposoft.webshop.domain.blog.PublicationStatus;
import hu.deposoft.webshop.domain.page.ContentPage;
import hu.deposoft.webshop.domain.page.ContentPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Optional;

/** Session-independent (cacheable) content-page reads for the public site (CLAUDE.md #2). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PageQueryService {

    private final ContentPageRepository pages;
    private final ObjectMapper objectMapper;

    public record PageView(String slug, String title, String bodyHtml,
                           String seoTitle, String seoDescription, String jsonLd) {}

    public Optional<PageView> getPublishedBySlug(String slug) {
        return pages.findBySlug(slug)
                .filter(p -> p.getStatus() == PublicationStatus.PUBLISHED)
                .map(this::toView);
    }

    private PageView toView(ContentPage p) {
        String description = p.getSeoDescription();
        LinkedHashMap<String, Object> ld = new LinkedHashMap<>();
        ld.put("@context", "https://schema.org");
        ld.put("@type", "WebPage");
        ld.put("name", p.getTitle());
        if (description != null) {
            ld.put("description", description);
        }
        String jsonLd = objectMapper.writeValueAsString(ld);
        return new PageView(p.getSlug(), p.getTitle(), p.getBodyHtml(),
                p.getSeoTitle() != null ? p.getSeoTitle() : p.getTitle(),
                description, jsonLd);
    }
}
