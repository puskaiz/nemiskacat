package hu.deposoft.webshop.application.blog;

import hu.deposoft.webshop.config.BlogHtmlBackfillRunner;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.blog.BlogPostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class BlogHtmlBackfillIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17");

    @Autowired BlogPostRepository posts;
    @Autowired MarkdownRenderer md;
    @Autowired BlogHtmlSanitizer sanitizer;

    @Test
    void backfillsHtmlFromMarkdown() {
        BlogPost p = BlogPost.create("backfill-x", "Cím");
        p.setBodyMarkdown("# H\n\nbody **b**");
        posts.save(p);

        int n = new BlogHtmlBackfillRunner().backfill(posts, md, sanitizer);

        assertThat(n).isGreaterThanOrEqualTo(1);
        BlogPost reloaded = posts.findBySlug("backfill-x").orElseThrow();
        assertThat(reloaded.getBodyHtml()).contains("<h1>H</h1>").contains("<strong>b</strong>");
    }
}
