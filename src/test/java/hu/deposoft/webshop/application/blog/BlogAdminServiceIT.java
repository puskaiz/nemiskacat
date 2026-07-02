package hu.deposoft.webshop.application.blog;

import hu.deposoft.webshop.application.blog.BlogAdminService.CategoryUpsert;
import hu.deposoft.webshop.application.blog.BlogAdminService.PostUpsert;
import hu.deposoft.webshop.domain.order.AuditEntryRepository;
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
class BlogAdminServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired BlogAdminService admin;
    @Autowired AuditEntryRepository auditEntries;

    private PostUpsert upsert(String slug) {
        return new PostUpsert(slug, "Cím", "kivonat", "# Törzs", null, null, List.of(), List.of());
    }

    @Test
    void createStartsAsDraft() {
        var detail = admin.create(upsert("uj-cikk"));
        assertThat(detail.status()).isEqualTo("DRAFT");
        assertThat(detail.id()).isNotNull();
    }

    @Test
    void duplicateSlugRejected() {
        admin.create(upsert("dup"));
        assertThatThrownBy(() -> admin.create(upsert("dup")))
                .isInstanceOf(BlogAdminService.SlugConflictException.class);
    }

    @Test
    void unknownRecommendedSkuRejected() {
        var u = new PostUpsert("with-sku", "C", "k", "b", null, null, List.of(),
                List.of("NO-SUCH-SKU"));
        assertThatThrownBy(() -> admin.create(u))
                .isInstanceOf(BlogAdminService.UnknownSkuException.class);
    }

    @Test
    void publishThenUnpublish() {
        var created = admin.create(upsert("lifecycle"));
        assertThat(admin.publish(created.id()).status()).isEqualTo("PUBLISHED");
        assertThat(admin.unpublish(created.id()).status()).isEqualTo("DRAFT");
    }

    @Test
    void getMissingThrowsNotFound() {
        assertThatThrownBy(() -> admin.get(999999L))
                .isInstanceOf(BlogAdminService.NotFoundException.class);
    }

    @Test
    void updateSlugConflictRejected_andOwnSlugAllowed() {
        // Create two posts with distinct slugs
        var a = admin.create(upsert("orig-a"));
        var b = admin.create(upsert("orig-b"));

        // Updating B to use A's slug must be rejected
        assertThatThrownBy(() -> admin.update(b.id(), upsert("orig-a")))
                .isInstanceOf(BlogAdminService.SlugConflictException.class);

        // Updating A to its OWN slug must succeed (no conflict)
        var updated = admin.update(a.id(), upsert("orig-a"));
        assertThat(updated.slug()).isEqualTo("orig-a");
    }

    @Test
    void getCategoryReturnsCategoryForValidId() {
        var created = admin.createCategory(new CategoryUpsert("Tech", "tech"));
        var fetched = admin.getCategory(created.id());
        assertThat(fetched.id()).isEqualTo(created.id());
        assertThat(fetched.name()).isEqualTo("Tech");
        assertThat(fetched.slug()).isEqualTo("tech");
    }

    @Test
    void getCategoryThrowsNotFoundForUnknownId() {
        assertThatThrownBy(() -> admin.getCategory(999999L))
                .isInstanceOf(BlogAdminService.NotFoundException.class);
    }

    @Test
    void createRecordsAuditEntry() {
        var detail = admin.create(upsert("audited"));

        var entries = auditEntries.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "blog_post", String.valueOf(detail.id()));
        assertThat(entries)
                .isNotEmpty()
                .anySatisfy(e -> {
                    assertThat(e.getAction()).isEqualTo("BLOG_POST_CREATE");
                    assertThat(e.getEntityType()).isEqualTo("blog_post");
                });
    }
}
