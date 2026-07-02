package hu.deposoft.webshop.application.content;

import hu.deposoft.webshop.application.catalog.ImageFetcher;
import hu.deposoft.webshop.application.catalog.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Downloads every {@code wp-content/uploads} reference into content-addressed storage and
 * rewrites it to {@code /media/<key>} (CLAUDE.md #8): both displayed {@code <img src>} and
 * linked {@code <a href>} (full-size images, PDFs and other uploaded files). External
 * references are left untouched. Shared by the blog, page, catalog and workshop imports.
 */
@Component
@RequiredArgsConstructor
public class InlineImageRewriter {

    private static final String UPLOADS_MARKER = "/wp-content/uploads/";

    private final ImageFetcher imageFetcher;
    private final StorageService storage;

    public record Result(String html, int stored, List<String> errors) {}

    public Result rewrite(String html) {
        List<String> errors = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return new Result(html == null ? "" : html, 0, errors);
        }
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        int stored = rewriteAttribute(doc, "img[src]", "src", errors)
                + rewriteAttribute(doc, "a[href]", "href", errors);
        return new Result(doc.body().html(), stored, errors);
    }

    /** Relocate every {@code selector}/{@code attr} value that points at wp-content/uploads. */
    private int rewriteAttribute(org.jsoup.nodes.Document doc, String selector, String attr,
                                 List<String> errors) {
        int stored = 0;
        for (org.jsoup.nodes.Element el : doc.select(selector)) {
            String url = el.attr(attr);
            if (!url.contains(UPLOADS_MARKER)) {
                continue;
            }
            try {
                ImageFetcher.FetchedImage fetched = imageFetcher.fetch(url);
                String key = storage.put(fetched.bytes(), fetched.contentType());
                el.attr(attr, "/media/" + key);
                stored++;
            } catch (RuntimeException e) {
                errors.add("inline %s %s: %s".formatted(attr, url, e.getMessage()));
            }
        }
        return stored;
    }
}
