package hu.deposoft.webshop.application.content;

import hu.deposoft.webshop.application.catalog.ImageFetcher;
import hu.deposoft.webshop.application.catalog.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Downloads every {@code wp-content/uploads} inline &lt;img&gt; into content-addressed
 * storage and rewrites its {@code src} to {@code /media/<key>} (CLAUDE.md #8); external
 * images are left untouched. Shared by the blog and content-page importers.
 */
@Component
@RequiredArgsConstructor
public class InlineImageRewriter {

    private final ImageFetcher imageFetcher;
    private final StorageService storage;

    public record Result(String html, int stored, List<String> errors) {}

    public Result rewrite(String html) {
        List<String> errors = new ArrayList<>();
        if (html == null || html.isBlank()) {
            return new Result(html == null ? "" : html, 0, errors);
        }
        int stored = 0;
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parseBodyFragment(html);
        doc.outputSettings().prettyPrint(false);
        for (org.jsoup.nodes.Element img : doc.select("img[src]")) {
            String url = img.attr("src");
            if (!url.contains("/wp-content/uploads/")) {
                continue;
            }
            try {
                ImageFetcher.FetchedImage fetched = imageFetcher.fetch(url);
                String key = storage.put(fetched.bytes(), fetched.contentType());
                img.attr("src", "/media/" + key);
                stored++;
            } catch (RuntimeException e) {
                errors.add("inline image %s: %s".formatted(url, e.getMessage()));
            }
        }
        return new Result(doc.body().html(), stored, errors);
    }
}
