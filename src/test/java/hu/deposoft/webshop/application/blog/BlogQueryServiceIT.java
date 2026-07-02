package hu.deposoft.webshop.application.blog;

import hu.deposoft.webshop.application.blog.BlogQueryService.BlogListItem;
import hu.deposoft.webshop.application.blog.BlogQueryService.BlogPostView;
import hu.deposoft.webshop.domain.blog.BlogCategory;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.blog.BlogPostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class BlogQueryServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired BlogQueryService blog;
    @Autowired BlogPostRepository posts;
    @Autowired BlogCategoryRepository categories;

    @Test
    void draftPostNotVisible() {
        var draft = BlogPost.create("rejtett", "Rejtett");
        posts.save(draft);
        assertThat(blog.getPublishedBySlug("rejtett")).isEmpty();
    }

    @Test
    void publishedPostReturnsStoredBodyHtml() {
        var p = BlogPost.create("latszik", "Látszik");
        p.setBodyHtml("<h1>Helló</h1>");
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        posts.save(p);

        Optional<BlogPostView> view = blog.getPublishedBySlug("latszik");
        assertThat(view).isPresent();
        assertThat(view.get().bodyHtml()).contains("<h1>Helló</h1>");
        assertThat(view.get().recommendedProducts()).isEmpty();
    }

    @Test
    void publishedPostBodyHtmlReturnedVerbatim() {
        var p = BlogPost.create("kesz", "Kész");
        p.setBodyHtml("<p>kész</p>");
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        posts.save(p);

        Optional<BlogPostView> view = blog.getPublishedBySlug("kesz");
        assertThat(view).isPresent();
        assertThat(view.get().bodyHtml()).isEqualTo("<p>kész</p>");
    }

    @Test
    void listShowsOnlyPublished() {
        // Save both a published and a draft post within this test to verify
        // that draft-exclusion is actively enforced, not just an artifact of
        // rollback isolation from other test methods.
        var published = BlogPost.create("listed-pub", "Közzétett");
        published.publish(OffsetDateTime.now(ZoneOffset.UTC));
        posts.save(published);

        var draft = BlogPost.create("listed-draft", "Piszkozat");
        posts.save(draft);

        assertThat(blog.publishedList(1).items())
                .extracting(BlogListItem::slug)
                .contains("listed-pub")
                .doesNotContain("listed-draft");
    }

    @Test
    void categoryListExcludesDrafts() {
        BlogCategory category = BlogCategory.create("Hírek", "hirek");
        categories.save(category);

        var published = BlogPost.create("hirek-pub", "Közzétett hír");
        published.publish(OffsetDateTime.now(ZoneOffset.UTC));
        published.setCategories(Set.of(category));
        posts.save(published);

        var draft = BlogPost.create("hirek-draft", "Piszkozat hír");
        draft.setCategories(Set.of(category));
        posts.save(draft);

        Optional<BlogQueryService.BlogListView> result = blog.publishedListByCategory("hirek", 1);
        assertThat(result).isPresent();
        assertThat(result.get().items())
                .extracting(BlogListItem::slug)
                .contains("hirek-pub")
                .doesNotContain("hirek-draft");

        assertThat(blog.publishedListByCategory("nincs-ilyen", 1)).isEmpty();
    }

    @Test
    void listExcerptDerivedWhenNoManualExcerpt() {
        var p = hu.deposoft.webshop.domain.blog.BlogPost.create("derivalt", "Derivált");
        p.setBodyHtml("<h1>Cím</h1><p>Ez a cikk bevezető szövege, amiből a kivonat származik.</p>");
        // no setExcerpt(...) -> excerpt is null
        p.publish(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        posts.save(p);

        var item = blog.publishedList(1).items().stream()
                .filter(i -> i.slug().equals("derivalt")).findFirst().orElseThrow();
        assertThat(item.excerpt()).isNotBlank();
        assertThat(item.excerpt()).contains("Ez a cikk bevezető szövege");
        assertThat(item.excerpt()).doesNotContain("<");
    }

    @Test
    void listExcerptUsesManualWhenPresent() {
        var p = hu.deposoft.webshop.domain.blog.BlogPost.create("kezi", "Kézi");
        p.setExcerpt("Kézi kivonat.");
        p.setBodyHtml("<p>Egészen más bevezető szöveg a body-ban.</p>");
        p.publish(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        posts.save(p);

        var item = blog.publishedList(1).items().stream()
                .filter(i -> i.slug().equals("kezi")).findFirst().orElseThrow();
        assertThat(item.excerpt()).isEqualTo("Kézi kivonat.");
    }

    @Test
    void postViewDescriptionDerivedWhenNoManualExcerptOrSeo() {
        var p = hu.deposoft.webshop.domain.blog.BlogPost.create("postview-derivalt", "PostView derivált");
        p.setBodyHtml("<h1>Cím</h1><p>Ez a cikk törzséből származó leírás a meta description-höz.</p>");
        // no setExcerpt, no setSeoDescription
        p.publish(java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC));
        posts.save(p);

        var view = blog.getPublishedBySlug("postview-derivalt").orElseThrow();
        assertThat(view.seoDescription()).isNotBlank();
        assertThat(view.seoDescription()).contains("Ez a cikk törzséből származó leírás");
        assertThat(view.jsonLd()).contains("Ez a cikk törzséből származó leírás"); // JSON-LD description populated
    }
}
