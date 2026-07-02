package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.instagram.InstagramFeedQuery;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.blog.BlogPostRepository;
import hu.deposoft.webshop.domain.page.ContentPage;
import hu.deposoft.webshop.domain.page.ContentPageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@Testcontainers
@Transactional
class RootSlugControllerIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @MockitoBean
    InstagramFeedQuery instagramFeedQuery;

    @Autowired WebApplicationContext context;
    @Autowired ContentPageRepository pages;
    @Autowired BlogPostRepository posts;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).build();
    }

    private ContentPage publishedPage(String slug, String title, String body) {
        ContentPage p = ContentPage.create(100L, slug, title);
        p.setBodyHtml(body);
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        return pages.save(p);
    }

    @Test
    void publishedPageRendersAtRootSlug() throws Exception {
        publishedPage("rolunk", "Rólunk", "<p>Bemutatkozás</p>");
        mvc.perform(get("/rolunk"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rólunk")))
                .andExpect(content().string(containsString("Bemutatkozás")))
                // body wrapper carries the shared rich-content class (same as blog) so 2-column layout renders
                .andExpect(content().string(containsString("nk-rich")));
    }

    @Test
    void draftPageReturns404() throws Exception {
        ContentPage p = ContentPage.create(101L, "titkos", "Titkos");
        p.setBodyHtml("<p>x</p>");
        pages.save(p); // stays DRAFT
        mvc.perform(get("/titkos")).andExpect(status().isNotFound());
    }

    @Test
    void unknownSlugReturns404() throws Exception {
        mvc.perform(get("/nincs-ilyen")).andExpect(status().isNotFound());
    }

    @Test
    void publishedBlogPostStillRendersAtRootSlug() throws Exception {
        BlogPost post = BlogPost.create("egy-cikk", "Egy cikk");
        post.setBodyHtml("<p>Cikktörzs</p>");
        post.publish(OffsetDateTime.now(ZoneOffset.UTC));
        posts.save(post);
        mvc.perform(get("/egy-cikk"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Egy cikk")))
                .andExpect(content().string(containsString("nk-blog-article")));
    }

    @Test
    void pageWinsOverBlogPostOnSharedSlug() throws Exception {
        publishedPage("kozos", "OLDAL CÍM", "<p>oldaltörzs</p>");
        BlogPost post = BlogPost.create("kozos", "POSZT CÍM");
        post.setBodyHtml("<p>poszttörzs</p>");
        post.publish(OffsetDateTime.now(ZoneOffset.UTC));
        posts.save(post);
        mvc.perform(get("/kozos"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nk-page-article")))
                .andExpect(content().string(containsString("OLDAL CÍM")));
    }
}
