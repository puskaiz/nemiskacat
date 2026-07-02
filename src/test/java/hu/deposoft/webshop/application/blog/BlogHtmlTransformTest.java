package hu.deposoft.webshop.application.blog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlogHtmlTransformTest {

    private final BlogHtmlTransform transform = new BlogHtmlTransform();

    @Test
    void blankInputReturnsEmpty() {
        assertThat(transform.normalize(null)).isEmpty();
        assertThat(transform.normalize("   ")).isEmpty();
    }

    @Test
    void gutenbergImageColumnsBecomeFigureRow() {
        String html = """
                <div class="wp-block-columns">
                  <div class="wp-block-column">
                    <figure class="wp-block-image size-full">
                      <img src="https://nemiskacat.hu/wp-content/uploads/2025/08/a.jpg" alt="Kép A"/>
                      <figcaption class="wp-element-caption">Horváth Ildikó munkája</figcaption>
                    </figure>
                  </div>
                  <div class="wp-block-column">
                    <figure class="wp-block-image size-large">
                      <img src="https://nemiskacat.hu/wp-content/uploads/2025/08/b.jpg" alt="Kép B"/>
                      <figcaption class="wp-element-caption">Aubusson Blue</figcaption>
                    </figure>
                  </div>
                </div>
                """;
        String result = transform.normalize(html);

        assertThat(result).contains("<div class=\"nk-figure-row\">");
        assertThat(result).contains("<img");
        assertThat(result).contains("a.jpg");
        assertThat(result).contains("b.jpg");
        assertThat(result).contains("<figure>");
        assertThat(result).contains("<figcaption>Horváth Ildikó munkája</figcaption>");
        assertThat(result).contains("<figcaption>Aubusson Blue</figcaption>");
        assertThat(result).contains("alt=\"Kép A\"");
        // original wp-block-columns wrapper must be gone
        assertThat(result).doesNotContain("wp-block-columns");
    }

    @Test
    void textColumnsLeftUnchanged() {
        String html = """
                <div class="wp-block-columns">
                  <div class="wp-block-column"><p>Bal szöveg</p></div>
                  <div class="wp-block-column"><p>Jobb szöveg</p></div>
                </div>
                """;
        String result = transform.normalize(html);

        assertThat(result).doesNotContain("nk-figure-row");
        assertThat(result).contains("Bal szöveg");
        assertThat(result).contains("Jobb szöveg");
    }

    @Test
    void stripsWpBlockComments() {
        String html = """
                <!-- wp:paragraph -->
                <p>Hello world</p>
                <!-- /wp:paragraph -->
                """;
        String result = transform.normalize(html);

        assertThat(result).doesNotContain("<!-- wp:");
        assertThat(result).contains("Hello world");
    }
}
