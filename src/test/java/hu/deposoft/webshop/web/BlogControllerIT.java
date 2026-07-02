package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.instagram.InstagramFeedQuery;
import hu.deposoft.webshop.application.instagram.InstagramPost;
import hu.deposoft.webshop.domain.blog.BlogCategory;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.blog.BlogPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * T7 acceptance: published post renders with Article JSON-LD; draft returns 404;
 * /blog list is reachable (200). Each test is fully self-contained (class-level
 * @Transactional rolls back per method).
 */
@SpringBootTest
@Testcontainers
@Transactional
class BlogControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @MockitoBean
    InstagramFeedQuery instagramFeedQuery;

    @Autowired
    WebApplicationContext context;

    @Autowired
    BlogPostRepository posts;

    @Autowired
    BlogCategoryRepository categories;

    @BeforeEach
    void setUpInstagramDefault() {
        // Default: disabled feed — returns empty list so the sidebar INSTAGRAM block renders nothing.
        when(instagramFeedQuery.latestPosts(anyInt())).thenReturn(List.of());
    }

    private MockMvc mvc() {
        return webAppContextSetup(context).build();
    }

    @Test
    void publishedPostRendersWithArticleJsonLd() throws Exception {
        BlogPost p = BlogPost.create("web-cikk", "Web cikk");
        p.setBodyMarkdown("# Tartalom");
        p.setBodyHtml("<h1>Tartalom</h1>\n"); // body_html is the rendered source; ADR 0009
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        posts.save(p);

        mvc().perform(get("/web-cikk"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"@type\":\"Article\"")))
                .andExpect(content().string(containsString("<h1>Tartalom</h1>")))
                // body wrapper carries the shared rich-content class so .nk-figure-row et al. render
                .andExpect(content().string(containsString("nk-rich")));
    }

    @Test
    void draftPostReturns404() throws Exception {
        BlogPost p = BlogPost.create("web-draft", "Draft");
        posts.save(p);

        mvc().perform(get("/web-draft")).andExpect(status().isNotFound());
    }

    @Test
    void unknownRootSlugReturns404() throws Exception {
        mvc().perform(get("/nincs-ilyen-cikk")).andExpect(status().isNotFound());
    }

    @Test
    void listAndCategoryRoutesStillResolve() throws Exception {
        // Regression guard: moving the post route to /{slug} must not affect the
        // literal /blog list nor the two-segment /blog/kategoria/{slug} category route.
        BlogCategory cat = BlogCategory.create("Hír kategória", "hir-kat");
        categories.save(cat);

        BlogPost p = BlogPost.create("hir-kat-post", "Hír kategória cikk");
        p.setBodyMarkdown("tartalom");
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        p.setCategories(Set.of(cat));
        posts.save(p);

        mvc().perform(get("/blog")).andExpect(status().isOk());
        mvc().perform(get("/blog/kategoria/hir-kat")).andExpect(status().isOk());
    }

    @Test
    void listIsReachable() throws Exception {
        mvc().perform(get("/blog")).andExpect(status().isOk());
    }

    @Test
    void categoryUnknownSlugReturns404() throws Exception {
        mvc().perform(get("/blog/kategoria/nincs-ilyen-kategoria"))
                .andExpect(status().isNotFound());
    }

    @Test
    void jsonLdEscapesQuotesInTitle() throws Exception {
        BlogPost p = BlogPost.create("idezojeles", "Macska \"prémium\" csomag");
        p.setBodyMarkdown("tartalom");
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        posts.save(p);

        mvc().perform(get("/idezojeles"))
                .andExpect(status().isOk())
                // Jackson backslash-escapes the double quotes inside the JSON string
                .andExpect(content().string(containsString("Macska \\\"prémium\\\" csomag")))
                // The JSON-LD script block must not contain HTML-entity-escaped quotes
                .andExpect(content().string(not(containsString("\"headline\":\"Macska &quot;"))));
    }

    @Test
    void categoryPaginationTrailingSlashResolves() throws Exception {
        BlogCategory cat = BlogCategory.create("Teszt kategória", "teszt-kat");
        categories.save(cat);

        BlogPost p = BlogPost.create("trailing-slash-cat-post", "Trailing slash category post");
        p.setBodyMarkdown("tartalom");
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        p.setCategories(Set.of(cat));
        posts.save(p);

        // URL with trailing slash before query string — must NOT return 404
        mvc().perform(get("/blog/kategoria/teszt-kat/?page=1"))
                .andExpect(status().isOk());
    }

    @Test
    void postTrailingSlashResolves() throws Exception {
        BlogPost p = BlogPost.create("trailing-slash-post", "Trailing slash post");
        p.setBodyMarkdown("tartalom");
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        posts.save(p);

        // URL with trailing slash — must NOT return 404
        mvc().perform(get("/trailing-slash-post/"))
                .andExpect(status().isOk());
    }

    @Test
    void articlePageHasNoCoverImage() throws Exception {
        // Seed a published post WITH a coverImageKey so the old template would render the cover.
        BlogPost p = BlogPost.create("web-cikk-cover", "Cover cikk");
        p.setBodyMarkdown("# Tartalom");
        p.setCoverImageKey("cover/test.jpg");
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        posts.save(p);

        mvc().perform(get("/web-cikk-cover"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("nk-blog-article__cover"))));
    }

    @Test
    void blogListRendersRowLayoutMarkup() throws Exception {
        var cat = hu.deposoft.webshop.domain.blog.BlogCategory.create("Hírek", "hirek");
        categories.save(cat);
        var p = hu.deposoft.webshop.domain.blog.BlogPost.create("listazott-cikk", "Listázott cikk");
        p.setExcerpt("Rövid leírás a listához.");
        p.setBodyMarkdown("törzs");
        p.publish(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        p.setCategories(java.util.Set.of(cat));
        posts.save(p);

        mvc().perform(get("/blog"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nk-blog-list")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nk-blog-card__media")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nk-blog-card__readmore")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tovább olvasom")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/listazott-cikk\"")));
    }

    @Test
    void blogPageHasContentAndEmptySidebarLayout() throws Exception {
        mvc().perform(get("/blog"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nk-blog-layout")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nk-blog-content")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nk-blog-sidebar")));
    }

    @Test
    void categoryPageAlsoHasSidebarLayout() throws Exception {
        var cat = hu.deposoft.webshop.domain.blog.BlogCategory.create("Hír", "hir-side");
        categories.save(cat);
        var p = hu.deposoft.webshop.domain.blog.BlogPost.create("hir-side-post", "Hír oldalsáv");
        p.setBodyMarkdown("tartalom");
        p.publish(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        p.setCategories(java.util.Set.of(cat));
        posts.save(p);
        mvc().perform(get("/blog/kategoria/hir-side"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nk-blog-sidebar")));
    }

    @Test
    void blogListRendersSidebarWithVisibleCategoriesOnly() throws Exception {
        categories.save(hu.deposoft.webshop.domain.blog.BlogCategory.create("Festéktan", "festektan"));
        var hidden = hu.deposoft.webshop.domain.blog.BlogCategory.create("Rejtett kategória", "rejtett-kat");
        hidden.setSidebarHidden(true);
        categories.save(hidden);

        mvc().perform(get("/blog"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nk-blog-sidebar")))
                .andExpect(content().string(containsString("nk-sb-author")))         // author block
                .andExpect(content().string(containsString("Festéktan")))           // visible category
                .andExpect(content().string(not(containsString("Rejtett kategória")))); // hidden absent
    }

    @Test
    void articlePageRendersSidebar() throws Exception {
        var p = hu.deposoft.webshop.domain.blog.BlogPost.create("oldalsav-cikk", "Oldalsáv cikk");
        p.publish(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        posts.save(p);

        mvc().perform(get("/oldalsav-cikk"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nk-blog-sidebar")))
                .andExpect(content().string(containsString("Kövess minket")));       // social block
    }

    @Test
    void galleryParagraphRendersMultipleImagesInOneParagraph() throws Exception {
        // Two images on ONE line in Markdown: flexmark renders them as two <img> in a single <p>.
        BlogPost p = BlogPost.create("galeria-cikk", "Galéria cikk");
        p.setBodyMarkdown("![a](/media/x.jpg) ![b](/media/y.jpg)");
        // body_html is the pre-rendered source (ADR 0009); set it to match the expected Markdown output
        p.setBodyHtml("<p><img src=\"/media/x.jpg\" alt=\"a\"> <img src=\"/media/y.jpg\" alt=\"b\"></p>\n");
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        posts.save(p);

        mvc().perform(get("/galeria-cikk"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.matchesPattern(
                        "(?s).*<p>\\s*<img[^>]*>\\s*<img[^>]*>.*")));
    }

    @Test
    void instagramSectionRendersWhenFeedHasPosts() throws Exception {
        List<InstagramPost> igPosts = List.of(
                new InstagramPost("p1", "Festési tipp", InstagramPost.MediaType.IMAGE,
                        "https://cdn.example.com/1.jpg", "https://www.instagram.com/p/abc/", Instant.now()),
                new InstagramPost("p2", null, InstagramPost.MediaType.IMAGE,
                        "https://cdn.example.com/2.jpg", "https://www.instagram.com/p/def/", Instant.now())
        );
        when(instagramFeedQuery.latestPosts(anyInt())).thenReturn(igPosts);

        mvc().perform(get("/blog"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nk-sb-instagram")))
                .andExpect(content().string(containsString("https://www.instagram.com/p/abc/")))
                .andExpect(content().string(containsString("https://cdn.example.com/1.jpg")));
    }

    @Test
    void instagramSectionAbsentWhenFeedIsEmpty() throws Exception {
        when(instagramFeedQuery.latestPosts(anyInt())).thenReturn(List.of());

        mvc().perform(get("/blog"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("nk-sb-instagram"))));
    }
}
