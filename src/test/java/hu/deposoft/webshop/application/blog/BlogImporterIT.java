package hu.deposoft.webshop.application.blog;

import hu.deposoft.webshop.application.catalog.ImageFetcher;
import hu.deposoft.webshop.domain.blog.BlogCategory;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.blog.BlogPostRepository;
import hu.deposoft.webshop.domain.blog.BlogTag;
import hu.deposoft.webshop.domain.blog.BlogTagRepository;
import hu.deposoft.webshop.domain.blog.PublicationStatus;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class BlogImporterIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    /** Deterministic fetcher: any URL -> fixed 1-byte PNG, so no real HTTP in tests. */
    @TestConfiguration
    static class FakeFetcherConfig {
        @Bean @Primary
        ImageFetcher fakeImageFetcher() {
            return url -> new ImageFetcher.FetchedImage(new byte[]{1, 2, 3}, "image/png");
        }
    }

    @Autowired BlogImporter importer;
    @Autowired BlogPostRepository posts;
    @Autowired BlogCategoryRepository categories;
    @Autowired BlogTagRepository tags;

    private static final OffsetDateTime PUB = OffsetDateTime.of(2024, 3, 1, 9, 0, 0, 0, ZoneOffset.UTC);

    private SourceBlogPost post(String slug, String title, String html, String status) {
        return new SourceBlogPost(1L, slug, title, "kivonat", html, status, PUB,
                null, null, null, List.of("hirek"), List.of());
    }

    private SourceBlog blog(SourceBlogPost... p) {
        return new SourceBlog(List.of(p), List.of(new SourceBlogCategory("Hírek", "hirek")), List.of());
    }

    @Test
    void importsPublishedPostStoringSanitizedHtml() {
        importer.run(blog(post("elso", "Első", "<h1>Cím</h1><p><strong>x</strong></p>", "publish")));
        BlogPost p = posts.findBySlug("elso").orElseThrow();
        assertThat(p.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(p.getPublishedAt()).isEqualTo(PUB);
        assertThat(p.getBodyHtml()).contains("<h1>Cím</h1>").contains("<strong>x</strong>");
        assertThat(p.getCategories()).extracting(c -> c.getSlug()).containsExactly("hirek");
    }

    @Test
    void draftPostImportedAsDraft() {
        importer.run(blog(post("piszk", "Piszkozat", "<p>szöveg</p>", "draft")));
        assertThat(posts.findBySlug("piszk").orElseThrow().getStatus()).isEqualTo(PublicationStatus.DRAFT);
    }

    @Test
    void secondRunIsIdempotent_noDuplicatesUpdatesInPlace() {
        importer.run(blog(post("egy", "Régi cím", "<p>a</p>", "publish")));
        BlogImportReport r2 = importer.run(blog(post("egy", "Új cím", "<p>b</p>", "publish")));
        assertThat(posts.findAll()).filteredOn(p -> p.getSlug().equals("egy")).hasSize(1);
        assertThat(posts.findBySlug("egy").orElseThrow().getTitle()).isEqualTo("Új cím");
        assertThat(r2.postsUpdated()).isEqualTo(1);
        assertThat(r2.postsCreated()).isZero();
    }

    @Test
    void uploadsImagesAreDownloadedAndRewritten_externalLeftAlone() {
        String html = "<p><img src=\"https://nemiskacat.hu/wp-content/uploads/2024/01/a.jpg\"></p>"
                + "<p><img src=\"https://other.example/x.png\"></p>";
        importer.run(blog(post("kepes", "Képes", html, "publish")));
        String body = posts.findBySlug("kepes").orElseThrow().getBodyHtml();
        assertThat(body).contains("<img src=\"/media/");
        assertThat(body).doesNotContain("wp-content/uploads");
        assertThat(body).contains("https://other.example/x.png");
    }

    @Test
    void coverImageDownloadedToStorage() {
        var p = new SourceBlogPost(1L, "borito", "Borító", "", "<p>x</p>", "publish", PUB,
                "https://nemiskacat.hu/wp-content/uploads/2024/01/cover.jpg", null, null, List.of(), List.of());
        importer.run(new SourceBlog(List.of(p), List.of(), List.of()));
        assertThat(posts.findBySlug("borito").orElseThrow().getCoverImageKey()).isNotBlank();
    }

    @Test
    void blankSlugPostIsSkippedNotPersisted() {
        BlogImportReport report = importer.run(blog(post("", "Slug nélküli", "<p>x</p>", "publish")));
        assertThat(report.postsSkipped()).isEqualTo(1);
        assertThat(report.postsCreated()).isZero();
        assertThat(report.errors()).isNotEmpty();
        assertThat(posts.findAll()).filteredOn(p -> p.getSlug() == null || p.getSlug().isBlank()).isEmpty();
    }

    @Test
    void importsAndLinksTagsIdempotently() {
        var post = new SourceBlogPost(1L, "tagos", "Tagos", "", "<p>x</p>", "publish", PUB,
                null, null, null, java.util.List.of(), java.util.List.of("vintage"));
        var source = new SourceBlog(java.util.List.of(post),
                java.util.List.of(),
                java.util.List.of(new hu.deposoft.webshop.integrations.wordpress.SourceBlogTag("Vintage", "vintage")));
        importer.run(source);
        importer.run(source); // re-run: idempotent

        var loaded = posts.findBySlug("tagos").orElseThrow();
        assertThat(loaded.getTags()).extracting(BlogTag::getSlug).containsExactly("vintage");
        assertThat(tags.findAll()).extracting(BlogTag::getSlug).containsExactlyInAnyOrder("vintage");
    }

    @Test
    void uncategorizedCategoryIsHiddenFromSidebarOnImport() {
        var source = new SourceBlog(
                List.of(),
                List.of(
                        new SourceBlogCategory("Hírek", "hirek"),
                        new SourceBlogCategory("Uncategorized", "uncategorized")
                ),
                List.of()
        );
        importer.run(source);

        BlogCategory hirek = categories.findBySlug("hirek").orElseThrow();
        BlogCategory uncategorized = categories.findBySlug("uncategorized").orElseThrow();

        assertThat(hirek.isSidebarHidden()).isFalse();
        assertThat(uncategorized.isSidebarHidden()).isTrue();
    }
}
