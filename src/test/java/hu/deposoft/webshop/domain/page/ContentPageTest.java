package hu.deposoft.webshop.domain.page;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.*;

class ContentPageTest {

    @Test
    void createRejectsInvalidSlug() {
        assertThatThrownBy(() -> ContentPage.create(1L, "Not A Slug", "T"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createStartsAsDraft() {
        ContentPage p = ContentPage.create(1L, "rolunk", "Rólunk");
        assertThat(p.getStatus()).isEqualTo(hu.deposoft.webshop.domain.blog.PublicationStatus.DRAFT);
        assertThat(p.getSlug()).isEqualTo("rolunk");
        assertThat(p.getExternalId()).isEqualTo(1L);
    }

    @Test
    void publishSetsTimestampOnceThenKeepsIt() {
        ContentPage p = ContentPage.create(1L, "rolunk", "Rólunk");
        OffsetDateTime first = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        p.publish(first);
        p.publish(OffsetDateTime.now(ZoneOffset.UTC));
        assertThat(p.getStatus()).isEqualTo(hu.deposoft.webshop.domain.blog.PublicationStatus.PUBLISHED);
        assertThat(p.getPublishedAt()).isEqualTo(first);
    }

    @Test
    void unpublishRevertsToDraftButRetainsPublishedAt() {
        ContentPage p = ContentPage.create(1L, "rolunk", "Rólunk");
        OffsetDateTime published = OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        p.publish(published);
        p.unpublish();
        assertThat(p.getStatus()).isEqualTo(hu.deposoft.webshop.domain.blog.PublicationStatus.DRAFT);
        // publishedAt is intentionally retained (set-once semantics, matches BlogPost):
        assertThat(p.getPublishedAt()).isEqualTo(published);
    }
}
