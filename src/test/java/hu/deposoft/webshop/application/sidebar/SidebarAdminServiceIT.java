package hu.deposoft.webshop.application.sidebar;

import hu.deposoft.webshop.application.sidebar.SidebarAdminService.CategoryVisibility;
import hu.deposoft.webshop.application.sidebar.SidebarAdminService.ContentUpdate;
import hu.deposoft.webshop.application.sidebar.SidebarAdminService.SidebarBlockView;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class SidebarAdminServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired SidebarAdminService admin;
    @Autowired BlogCategoryRepository categories;

    @Test
    void listReturnsAllSeededBlocksIncludingDisabledInOrder() {
        // disable one, confirm it still appears in the admin list (unlike the public read)
        SidebarBlockView social = admin.list().stream()
                .filter(b -> b.blockType().equals("SOCIAL")).findFirst().orElseThrow();
        admin.setEnabled(social.id(), false);
        assertThat(admin.list()).extracting(SidebarBlockView::blockType)
                .containsExactly("AUTHOR", "CATEGORIES", "CTA", "CONTACT", "SOCIAL", "INSTAGRAM");
        assertThat(admin.list().stream().filter(b -> b.blockType().equals("SOCIAL"))
                .findFirst().orElseThrow().enabled()).isFalse();
    }

    @Test
    void updateContentValidatesJsonForType() {
        SidebarBlockView author = admin.list().get(0); // AUTHOR
        SidebarBlockView updated = admin.updateContent(author.id(),
                new ContentUpdate("{\"name\":\"Teszt Elek\",\"bio\":\"új\",\"photoUrl\":\"/x.svg\"}"));
        assertThat(updated.content()).contains("Teszt Elek");
        // malformed JSON for the type -> 400-mapped IllegalArgumentException
        assertThatThrownBy(() -> admin.updateContent(author.id(), new ContentUpdate("{not json")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contactBlockAcceptsAndStoresOpeningHours() {
        SidebarBlockView contact = admin.list().stream()
                .filter(b -> b.blockType().equals("CONTACT")).findFirst().orElseThrow();
        SidebarBlockView updated = admin.updateContent(contact.id(), new ContentUpdate(
                "{\"title\":\"Elérhetőség\",\"phone\":\"+36 1\",\"email\":\"a@b.hu\",\"address\":\"Bp.\","
                        + "\"openingHours\":\"H–P: 9–17\\nSzo: 9–13\"}"));
        assertThat(updated.content()).contains("openingHours").contains("Szo: 9–13");
    }

    @Test
    void updateContentRejectsWrongTypeJson() {
        SidebarBlockView author = admin.list().get(0); // AUTHOR
        // valid JSON but SOCIAL-shaped (foreign fields) must be rejected for an AUTHOR block
        assertThatThrownBy(() -> admin.updateContent(author.id(),
                new ContentUpdate("{\"title\":\"x\",\"links\":[]}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateContentRejectsEmptyObject() {
        SidebarBlockView author = admin.list().get(0); // AUTHOR
        assertThatThrownBy(() -> admin.updateContent(author.id(), new ContentUpdate("{}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateContentRejectsNull() {
        SidebarBlockView author = admin.list().get(0); // AUTHOR
        assertThatThrownBy(() -> admin.updateContent(author.id(), new ContentUpdate(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reorderPersistsNewDisplayOrder() {
        List<Long> ids = admin.list().stream().map(SidebarBlockView::id).toList();
        List<Long> reversed = ids.reversed();
        List<SidebarBlockView> after = admin.reorder(reversed);
        assertThat(after).extracting(SidebarBlockView::id).containsExactlyElementsOf(reversed);
        // a reorder list that isn't exactly the existing id set is rejected
        assertThatThrownBy(() -> admin.reorder(List.of(ids.get(0))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void categoryVisibilityReadAndSet() {
        categories.save(BlogCategory.create("Festés", "festes-admin"));
        assertThat(admin.categories()).extracting(CategoryVisibility::slug).contains("festes-admin");
        CategoryVisibility v = admin.setCategoryVisibility("festes-admin", true);
        assertThat(v.sidebarHidden()).isTrue();
        assertThat(categories.findBySlug("festes-admin").orElseThrow().isSidebarHidden()).isTrue();
    }

    @Test
    void getUnknownThrowsNotFound() {
        assertThatThrownBy(() -> admin.get(999999L))
                .isInstanceOf(SidebarAdminService.NotFoundException.class);
    }
}
