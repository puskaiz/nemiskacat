package hu.deposoft.webshop.application.content;

import hu.deposoft.webshop.application.catalog.ImageFetcher;
import hu.deposoft.webshop.application.catalog.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the shared inline-asset rewriter: every {@code /wp-content/uploads/}
 * reference in the HTML — both displayed {@code <img src>} and linked {@code <a href>}
 * (full-size images, PDFs) — is pulled into storage and rewritten to {@code /media/…};
 * external references are left untouched. No Spring, no network: in-memory fakes.
 */
class InlineImageRewriterTest {

    private final ImageFetcher fetcher = url -> {
        String ct = url.toLowerCase().endsWith(".pdf") ? "application/pdf" : "image/jpeg";
        return new ImageFetcher.FetchedImage(url.getBytes(), ct);
    };

    private int seq = 0;
    private final StorageService storage = new StorageService() {
        @Override
        public String put(byte[] bytes, String contentType) {
            String ext = "application/pdf".equals(contentType) ? "pdf" : "jpg";
            return "up/asset" + (seq++) + "." + ext;
        }

        @Override public Resource load(String key) { return null; }
        @Override public void delete(String key) { }
        @Override public boolean exists(String key) { return false; }
    };

    private final InlineImageRewriter rewriter = new InlineImageRewriter(fetcher, storage);

    @Test
    void rewritesAnchorHrefIntoUploads() {
        String html = "<p><a href=\"https://nemiskacat.hu/wp-content/uploads/2023/05/guide.pdf\">Letöltés</a></p>";

        InlineImageRewriter.Result result = rewriter.rewrite(html);

        assertThat(result.html()).contains("/media/up/").doesNotContain("wp-content/uploads");
        assertThat(result.stored()).isEqualTo(1);
    }

    @Test
    void rewritesBothImgSrcAndWrappingAnchorHref() {
        String html = "<a href=\"https://nemiskacat.hu/wp-content/uploads/full.jpg\">"
                + "<img src=\"https://nemiskacat.hu/wp-content/uploads/thumb.jpg\"></a>";

        InlineImageRewriter.Result result = rewriter.rewrite(html);

        assertThat(result.html()).doesNotContain("wp-content/uploads");
        assertThat(result.stored()).isEqualTo(2);
    }

    @Test
    void leavesExternalAnchorsUntouched() {
        String html = "<a href=\"https://example.com/other.pdf\">x</a>";

        InlineImageRewriter.Result result = rewriter.rewrite(html);

        assertThat(result.html()).contains("https://example.com/other.pdf");
        assertThat(result.stored()).isZero();
    }
}
