package hu.deposoft.webshop.application.blog;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BlogHtmlSanitizerTest {
    private final BlogHtmlSanitizer s = new BlogHtmlSanitizer();

    @Test void stripsScriptAndEventHandlers() {
        assertThat(s.sanitize("<p onclick=\"x()\">hi</p><script>evil()</script>"))
                .isEqualTo("<p>hi</p>");
    }
    @Test void keepsAllowedFormatting() {
        String in = "<h2>Cím</h2><p>egy <strong>bold</strong> <a href=\"https://x.hu\">link</a></p><ul><li>a</li></ul>";
        String out = s.sanitize(in);
        assertThat(out).contains("<h2>Cím</h2>").contains("<strong>bold</strong>")
                .contains("<a href=\"https://x.hu\"").contains("<ul>").contains("<li>a</li>");
    }
    @Test void keepsFigureRowLayoutAndImages() {
        String in = "<div class=\"nk-figure-row\"><figure><img src=\"/media/abc\" alt=\"k\"><figcaption>cap</figcaption></figure></div>";
        String out = s.sanitize(in);
        assertThat(out).contains("class=\"nk-figure-row\"").contains("<figure>")
                .contains("<img").contains("/media/abc").contains("<figcaption>cap</figcaption>");
    }
    @Test void keepsTables() {
        String in = "<table><thead><tr><th>a</th></tr></thead><tbody><tr><td>b</td></tr></tbody></table>";
        assertThat(s.sanitize(in)).contains("<table>").contains("<th>a</th>").contains("<td>b</td>");
    }
    @Test void dropsDisallowedClassButKeepsElement() {
        assertThat(s.sanitize("<div class=\"evil wp-block-foo\">x</div>")).isEqualTo("<div>x</div>");
    }
    @Test void blankReturnsEmpty() {
        assertThat(s.sanitize(null)).isEmpty();
        assertThat(s.sanitize("  ")).isEmpty();
    }
    @Test void stripsJavascriptHref() {
        String out = s.sanitize("<a href=\"javascript:alert(1)\">x</a>");
        assertThat(out).doesNotContain("javascript:");
    }
    @Test void stripsDataUriImgSrc() {
        String out = s.sanitize("<img src=\"data:text/html;base64,PHNjcmlwdD4=\" alt=\"a\">");
        assertThat(out).doesNotContain("data:");
    }

    /**
     * Regression guard: feeds a representative slice of TipTap StarterKit + our extensions output
     * through the sanitizer and asserts that all legitimate structural elements survive (no silent
     * data loss). Specifically guards the hr bug where jsoup relaxed() dropped horizontal rules.
     */
    @Test void tiptapStarterKitOutputSurvives() {
        String in =
                "<h2>Heading</h2>" +
                "<p><strong>bold</strong> <em>italic</em> <s>strike</s> <u>under</u></p>" +
                "<ul><li>item</li></ul>" +
                "<blockquote><p>quote</p></blockquote>" +
                "<pre><code>code block</code></pre>" +
                "<hr>" +
                "<a href=\"https://x.hu\">link</a>" +
                "<img src=\"/media/k\" alt=\"a\">" +
                "<table><thead><tr><th>H</th></tr></thead><tbody><tr><td>D</td></tr></tbody></table>" +
                "<div class=\"nk-figure-row\"><figure><img src=\"/media/k2\" alt=\"b\"><figcaption>cap</figcaption></figure></div>";

        String out = s.sanitize(in);

        // hr — primary regression guard for the silent-drop bug
        assertThat(out).contains("<hr>");
        // inline formatting
        assertThat(out).contains("<s>").contains("</s>");
        assertThat(out).contains("<u>").contains("</u>");
        // code block
        assertThat(out).contains("<pre>").contains("<code>");
        // table elements
        assertThat(out).contains("<table>").contains("<th>").contains("<td>");
        // figure / figcaption
        assertThat(out).contains("<figure>").contains("<figcaption>");
        // allowed class preserved
        assertThat(out).contains("class=\"nk-figure-row\"");
        // image src paths preserved (relative /media/ URLs)
        assertThat(out).contains("/media/k").contains("/media/k2");
        // link href preserved
        assertThat(out).contains("href=\"https://x.hu\"");
    }
}
