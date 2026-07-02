package hu.deposoft.webshop.application.blog;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test
    void rendersHeadingAndParagraph() {
        String html = renderer.toHtml("# Cím\n\nSzöveg **félkövér**.");
        assertThat(html).contains("<h1>Cím</h1>");
        assertThat(html).contains("<strong>félkövér</strong>");
    }

    @Test
    void blankInputRendersEmptyString() {
        assertThat(renderer.toHtml(null)).isEmpty();
        assertThat(renderer.toHtml("   ")).isEmpty();
    }

    @Test
    void rendersGfmPipeTable() {
        String md = """
                | Termék | Ár      |
                |--------|---------|
                | Tea    | 990 Ft  |
                """;
        String html = renderer.toHtml(md);
        assertThat(html).contains("<table>");
        assertThat(html).contains("<th>Termék</th>");
        assertThat(html).contains("<td>990 Ft</td>");
    }
}
