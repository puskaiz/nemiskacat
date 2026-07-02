package hu.deposoft.webshop.application.sidebar;

import hu.deposoft.webshop.application.sidebar.SidebarQueryService.SidebarBlockView;
import hu.deposoft.webshop.application.sidebar.SidebarQueryService.SidebarView;
import hu.deposoft.webshop.domain.blog.BlogCategory;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class SidebarQueryServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired SidebarQueryService sidebar;
    @Autowired BlogCategoryRepository categories;

    @Test
    void blocksAreOrderedAndAuthorParsed() {
        SidebarView view = sidebar.sidebar();
        // V26 adds INSTAGRAM as the 6th block (disabled feed → posts empty, view still built)
        assertThat(view.blocks()).extracting(SidebarBlockView::type)
                .containsExactly("AUTHOR", "CATEGORIES", "CTA", "CONTACT", "SOCIAL", "INSTAGRAM");
        SidebarBlockView author = view.blocks().get(0);
        assertThat(author.author().name()).isEqualTo("Szendrődi Enikő");
        assertThat(author.author().bio()).contains("hobbibútorfestő");
        // V27 sets the goals text and points the photo at the transferred asset.
        assertThat(author.author().goals()).contains("bútorfestők");
        assertThat(author.author().photoUrl()).isEqualTo("/design/assets/blog/author-eniko.jpg");
        // V30 sets the CTA description; V31 corrects the image to the chalk-paint tin.
        SidebarBlockView cta = view.blocks().get(2);
        assertThat(cta.cta().description()).contains("webshopjában");
        assertThat(cta.cta().imageUrl()).isEqualTo("/design/assets/blog/cta-paint.jpg");
    }

    @Test
    void categoriesBlockAttachesVisibleAlphabeticalCategories() {
        categories.save(BlogCategory.create("Zebra", "zebra"));
        BlogCategory hidden = BlogCategory.create("Alma", "alma");
        hidden.setSidebarHidden(true);
        categories.save(hidden);
        categories.save(BlogCategory.create("Béka", "beka"));

        SidebarBlockView cats = sidebar.sidebar().blocks().stream()
                .filter(b -> b.type().equals("CATEGORIES")).findFirst().orElseThrow();
        assertThat(cats.categories().title()).isEqualTo("Kategóriák");
        assertThat(cats.categories().items())
                .extracting(c -> c.name())
                .containsSubsequence("Béka", "Zebra")
                .doesNotContain("Alma");
    }

    @Test
    void socialAndContactParsed() {
        SidebarView view = sidebar.sidebar();
        SidebarBlockView social = view.blocks().stream()
                .filter(b -> b.type().equals("SOCIAL")).findFirst().orElseThrow();
        assertThat(social.social().links()).extracting(l -> l.network())
                .contains("facebook", "instagram", "youtube");
        SidebarBlockView contact = view.blocks().stream()
                .filter(b -> b.type().equals("CONTACT")).findFirst().orElseThrow();
        assertThat(contact.contact().phone()).isEqualTo("+36 20 269 9113");
        // Seeded content predates openingHours -> must deserialize to null, not fail.
        assertThat(contact.contact().openingHours()).isNull();
    }

    @Test
    void socialBlockLinksComeFromTheStore() {
        var social = sidebar.sidebar().blocks().stream()
                .filter(b -> b.type().equals("SOCIAL")).findFirst().orElseThrow();
        assertThat(social.social().links()).extracting(l -> l.network())
                .containsExactly("facebook", "instagram", "youtube");
    }

    @Test
    void instagramBlockWithDisabledFeedYieldsEmptyPosts() {
        // Default state: instagram.enabled=false — DisabledInstagramFeedQuery returns List.of()
        SidebarBlockView igBlock = sidebar.sidebar().blocks().stream()
                .filter(b -> b.type().equals("INSTAGRAM")).findFirst().orElseThrow();
        assertThat(igBlock.instagram()).isNotNull();
        assertThat(igBlock.instagram().title()).isEqualTo("Kövess minket Instagramon");
        assertThat(igBlock.instagram().posts()).isEmpty();
    }
}
