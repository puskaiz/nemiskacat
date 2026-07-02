package hu.deposoft.webshop.application.blog;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Single sanitization point for all stored blog HTML (admin save + import).
 * Allowlist-based (jsoup): unknown tags/attributes are dropped, so the rendered
 * body can never contain script/style/event handlers/unknown embeds.
 */
@Component
public class BlogHtmlSanitizer {

    private static final java.util.Set<String> ALLOWED_CLASSES =
            java.util.Set.of("nk-figure-row");

    private final Safelist safelist = buildSafelist();

    public String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        // prettyPrint(false): keep output compact and stable for storage/diffing.
        org.jsoup.nodes.Document.OutputSettings settings =
                new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false);
        // Base URI allows jsoup to resolve relative /media/* paths for protocol
        // checking (http/https) while preserveRelativeLinks keeps them relative in output.
        String cleaned = Jsoup.clean(html, "https://x", safelist, settings);
        // Re-parse to filter class tokens; apply same compact output settings.
        org.jsoup.nodes.Document doc = Jsoup.parseBodyFragment(cleaned);
        doc.outputSettings().prettyPrint(false);
        for (org.jsoup.nodes.Element el : doc.select("[class]")) {
            String kept = el.classNames().stream()
                    .filter(ALLOWED_CLASSES::contains)
                    .reduce((a, b) -> a + " " + b).orElse("");
            if (kept.isBlank()) {
                el.removeAttr("class");
            } else {
                el.attr("class", kept);
            }
        }
        return doc.body().html();
    }

    private static Safelist buildSafelist() {
        Safelist sl = Safelist.relaxed()
                // relaxed() allows h1-6, p, ul/ol/li, blockquote, a, img, strong/em,
                // code, pre, table family — but not figure/figcaption/hr/s/u; add them explicitly.
                // s (strikethrough) and u (underline) are standard TipTap StarterKit inline marks.
                .addTags("figure", "figcaption", "hr", "s", "u")
                .addAttributes("a", "href", "title", "target", "rel")
                .addAttributes("img", "src", "alt", "title", "width", "height")
                .addAttributes("div", "class")
                .addAttributes("figure", "class")
                .addProtocols("a", "href", "http", "https", "mailto")
                .addProtocols("img", "src", "http", "https")
                // images are served same-origin from /media — allow relative URLs
                .preserveRelativeLinks(true);
        return sl;
    }
}
