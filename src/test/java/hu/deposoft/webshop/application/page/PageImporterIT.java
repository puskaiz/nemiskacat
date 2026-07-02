package hu.deposoft.webshop.application.page;

import hu.deposoft.webshop.application.catalog.ImageFetcher;
import hu.deposoft.webshop.application.catalog.StorageService;
import hu.deposoft.webshop.domain.blog.PublicationStatus;
import hu.deposoft.webshop.domain.page.ContentPageRepository;
import hu.deposoft.webshop.integrations.wordpress.SourcePage;
import hu.deposoft.webshop.integrations.wordpress.SourcePages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@Transactional
class PageImporterIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired PageImporter importer;
    @Autowired ContentPageRepository pages;
    @Autowired PageQueryService query;

    @MockitoBean ImageFetcher imageFetcher;
    @MockitoBean StorageService storage;

    private SourcePage page(long id, String slug, String title, String body, String status) {
        return new SourcePage(id, slug, title, body, status,
                OffsetDateTime.of(2021, 6, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                "SEO " + title, "Leírás " + title);
    }

    @Test
    void importsAndPublishesPage() {
        importer.run(new SourcePages(List.of(
                page(1, "rolunk", "Rólunk", "<p>Szia</p>", "publish"))));
        var view = query.getPublishedBySlug("rolunk");
        assertThat(view).isPresent();
        assertThat(view.get().title()).isEqualTo("Rólunk");
        assertThat(view.get().bodyHtml()).contains("Szia");
    }

    @Test
    void reimportIsIdempotentAndUpdatesTitleButNotSlug() {
        importer.run(new SourcePages(List.of(page(1, "rolunk", "Rólunk", "<p>a</p>", "publish"))));
        // same externalId, changed slug + title on re-import
        importer.run(new SourcePages(List.of(page(1, "rolunk-uj", "Rólunk 2", "<p>b</p>", "publish"))));
        assertThat(pages.findAll()).hasSize(1);
        var stored = pages.findByExternalId(1L).orElseThrow();
        assertThat(stored.getSlug()).isEqualTo("rolunk");   // slug immutable (CLAUDE.md #7)
        assertThat(stored.getTitle()).isEqualTo("Rólunk 2"); // other fields refresh
    }

    @Test
    void draftIsHiddenFromPublicQuery() {
        importer.run(new SourcePages(List.of(page(2, "titkos", "Titkos", "<p>x</p>", "draft"))));
        assertThat(query.getPublishedBySlug("titkos")).isEmpty();
        assertThat(pages.findBySlug("titkos").orElseThrow().getStatus())
                .isEqualTo(PublicationStatus.DRAFT);
    }

    @Test
    void sanitizesBodyHtml() {
        importer.run(new SourcePages(List.of(
                page(3, "tiszta", "Tiszta", "<p>ok</p><script>alert(1)</script>", "publish"))));
        assertThat(pages.findBySlug("tiszta").orElseThrow().getBodyHtml())
                .doesNotContain("<script>");
    }

    @Test
    void rewritesUploadImagesAndKeepsExternal() {
        when(imageFetcher.fetch(anyString()))
                .thenReturn(new ImageFetcher.FetchedImage("x".getBytes(), "image/jpeg"));
        when(storage.put(org.mockito.ArgumentMatchers.any(), anyString())).thenReturn("KEY1");
        String body = "<p><img src=\"https://nemiskacat.hu/wp-content/uploads/a.jpg\">"
                + "<img src=\"https://other.example/b.jpg\"></p>";
        importer.run(new SourcePages(List.of(page(4, "kepek", "Képek", body, "publish"))));
        String stored = pages.findBySlug("kepek").orElseThrow().getBodyHtml();
        assertThat(stored).contains("/media/KEY1");
        assertThat(stored).contains("https://other.example/b.jpg");
    }
}
