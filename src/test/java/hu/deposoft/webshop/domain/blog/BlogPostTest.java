package hu.deposoft.webshop.domain.blog;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlogPostTest {

    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 6, 29, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    void newPostIsDraftWithNoPublishedAt() {
        BlogPost post = BlogPost.create("elso-cikk", "Első cikk");
        assertThat(post.getStatus()).isEqualTo(PublicationStatus.DRAFT);
        assertThat(post.getPublishedAt()).isNull();
    }

    @Test
    void publishSetsStatusAndTimestamp() {
        BlogPost post = BlogPost.create("elso-cikk", "Első cikk");
        post.publish(NOW);
        assertThat(post.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(post.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void republishKeepsOriginalPublishedAt() {
        BlogPost post = BlogPost.create("elso-cikk", "Első cikk");
        post.publish(NOW);
        post.unpublish();
        post.publish(NOW.plusDays(5));
        assertThat(post.getStatus()).isEqualTo(PublicationStatus.PUBLISHED);
        assertThat(post.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    void unpublishReturnsToDraft() {
        BlogPost post = BlogPost.create("elso-cikk", "Első cikk");
        post.publish(NOW);
        post.unpublish();
        assertThat(post.getStatus()).isEqualTo(PublicationStatus.DRAFT);
    }

    @Test
    void invalidSlugRejected() {
        assertThatThrownBy(() -> BlogPost.create("Bad Slug!", "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(BlogPost.isValidSlug("jo-slug-123")).isTrue();
        assertThat(BlogPost.isValidSlug("Rossz_Slug")).isFalse();
    }
}
