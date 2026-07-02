package hu.deposoft.webshop.application.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The fetcher must keep the server's content type when it is a servable asset (images and
 * PDFs), and otherwise guess from the URL extension — so a linked PDF is not mislabelled as
 * an image (which would give it a .jpg storage key and the wrong Content-Type on serve).
 */
class HttpImageFetcherTest {

    @Test
    void keepsPdfContentTypeFromHeader() {
        assertThat(HttpImageFetcher.resolveContentType("application/pdf", "https://x/guide"))
                .isEqualTo("application/pdf");
    }

    @Test
    void keepsImageContentTypeFromHeaderStrippingParams() {
        assertThat(HttpImageFetcher.resolveContentType("image/png; charset=binary", "https://x/a"))
                .isEqualTo("image/png");
    }

    @Test
    void ignoresNonServableHeaderAndFallsBackToPdfUrlExtension() {
        // e.g. a misconfigured server returns text/html for a .pdf link
        assertThat(HttpImageFetcher.resolveContentType("text/html", "https://x/guide.pdf"))
                .isEqualTo("application/pdf");
    }

    @Test
    void guessesPngFromUrlWhenHeaderAbsent() {
        assertThat(HttpImageFetcher.resolveContentType(null, "https://x/photo.PNG"))
                .isEqualTo("image/png");
    }
}
