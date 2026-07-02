package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.blog.BlogHtmlSanitizer;
import hu.deposoft.webshop.application.blog.MarkdownRenderer;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.blog.BlogPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time backfill: render existing body_markdown -> HTML (flexmark) -> sanitize
 * -> body_html, for rows whose body_html is still empty. Non-destructive; preserves
 * any manual Markdown edits. Run with profile {@code backfill-blog-html}, then exit.
 */
@Configuration
@Profile("backfill-blog-html")
public class BlogHtmlBackfillRunner {
    private static final Logger log = LoggerFactory.getLogger(BlogHtmlBackfillRunner.class);

    @Bean
    CommandLineRunner backfillBlogHtml(BlogPostRepository posts, MarkdownRenderer md,
                                       BlogHtmlSanitizer sanitizer, ApplicationContext ctx) {
        return args -> {
            int n = backfill(posts, md, sanitizer);
            log.info("Blog HTML backfill complete: {} posts updated", n);
            System.exit(SpringApplication.exit(ctx, () -> 0));
        };
    }

    @Transactional
    public int backfill(BlogPostRepository posts, MarkdownRenderer md, BlogHtmlSanitizer sanitizer) {
        int n = 0;
        for (BlogPost p : posts.findAll()) {
            if (p.getBodyHtml() == null || p.getBodyHtml().isBlank()) {
                p.setBodyHtml(sanitizer.sanitize(md.toHtml(p.getBodyMarkdown())));
                posts.save(p);
                n++;
            }
        }
        return n;
    }
}
