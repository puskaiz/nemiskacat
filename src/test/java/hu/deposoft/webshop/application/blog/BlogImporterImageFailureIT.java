package hu.deposoft.webshop.application.blog;

import hu.deposoft.webshop.application.catalog.ImageFetcher;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.blog.BlogPostRepository;
import hu.deposoft.webshop.integrations.wordpress.SourceBlog;
import hu.deposoft.webshop.integrations.wordpress.SourceBlogCategory;
import hu.deposoft.webshop.integrations.wordpress.SourceBlogPost;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a per-image fetch failure is isolated: the importer records the
 * error but still creates the post and continues the run.
 */
@SpringBootTest
@Testcontainers
@Transactional
class BlogImporterImageFailureIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    /** Fetcher that always throws — simulates a network/HTTP failure for every image. */
    @TestConfiguration
    static class ThrowingFetcherConfig {
        @Bean @Primary
        ImageFetcher throwingImageFetcher() {
            return url -> { throw new RuntimeException("boom"); };
        }
    }

    @Autowired BlogImporter importer;
    @Autowired BlogPostRepository posts;

    private static final OffsetDateTime PUB = OffsetDateTime.of(2024, 3, 1, 9, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void imageFetchFailureIsIsolated_postStillCreated() {
        String html = "<p><img src=\"https://nemiskacat.hu/wp-content/uploads/2024/01/a.jpg\"></p>";
        SourceBlogPost post = new SourceBlogPost(
                42L, "kepes-post", "Képes poszt", "kivonat", html,
                "publish", PUB, null, null, null, List.of("hirek"), List.of());
        SourceBlog blog = new SourceBlog(
                List.of(post),
                List.of(new SourceBlogCategory("Hírek", "hirek")),
                List.of());

        BlogImportReport report = importer.run(blog);

        // Image fetch failed — error recorded
        assertThat(report.errors()).isNotEmpty();
        // Post was still imported despite the failure
        assertThat(report.postsCreated()).isEqualTo(1);
        BlogPost saved = posts.findBySlug("kepes-post").orElseThrow();
        assertThat(saved).isNotNull();
        // Original URL retained (not rewritten) because fetch failed
        assertThat(saved.getBodyHtml())
                .contains("https://nemiskacat.hu/wp-content/uploads/2024/01/a.jpg");
    }
}
