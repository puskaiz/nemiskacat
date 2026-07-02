package hu.deposoft.webshop.integrations.instagram;

import hu.deposoft.webshop.application.instagram.InstagramPost;
import hu.deposoft.webshop.domain.instagram.InstagramMedia;
import hu.deposoft.webshop.domain.instagram.InstagramMediaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link DbInstagramFeedQuery}: verifies entity → record mapping,
 * including null caption and VIDEO type.
 */
@ExtendWith(MockitoExtension.class)
class DbInstagramFeedQueryTest {

    @Mock
    InstagramMediaRepository repo;

    private DbInstagramFeedQuery query() {
        return new DbInstagramFeedQuery(repo);
    }

    private static final Instant TAKEN_AT = Instant.parse("2024-06-15T10:00:00Z");

    @Test
    void mapsImageRowToInstagramPost() {
        InstagramMedia media = InstagramMedia.create(
                "abc123",
                "My caption",
                "IMAGE",
                "https://cdn.example/photo.jpg",
                "https://www.instagram.com/p/abc123",
                TAKEN_AT,
                1,
                Instant.now());

        when(repo.findAllByOrderByPositionAsc(any(Limit.class))).thenReturn(List.of(media));

        List<InstagramPost> posts = query().latestPosts(5);

        assertThat(posts).hasSize(1);
        InstagramPost post = posts.getFirst();
        assertThat(post.id()).isEqualTo("abc123");
        assertThat(post.caption()).isEqualTo("My caption");
        assertThat(post.mediaType()).isEqualTo(InstagramPost.MediaType.IMAGE);
        assertThat(post.displayUrl()).isEqualTo("https://cdn.example/photo.jpg");
        assertThat(post.permalink()).isEqualTo("https://www.instagram.com/p/abc123");
        assertThat(post.timestamp()).isEqualTo(TAKEN_AT);
        verify(repo).findAllByOrderByPositionAsc(Limit.of(5));
    }

    @Test
    void mapsVideoRowWithNullCaption() {
        InstagramMedia media = InstagramMedia.create(
                "vid999",
                null,                               // null caption
                "VIDEO",
                "https://cdn.example/thumb.jpg",
                "https://www.instagram.com/p/vid999",
                TAKEN_AT,
                1,
                Instant.now());

        when(repo.findAllByOrderByPositionAsc(any(Limit.class))).thenReturn(List.of(media));

        List<InstagramPost> posts = query().latestPosts(1);

        assertThat(posts).hasSize(1);
        InstagramPost post = posts.getFirst();
        assertThat(post.mediaType()).isEqualTo(InstagramPost.MediaType.VIDEO);
        assertThat(post.caption()).isNull();
    }

    @Test
    void mapsCarouselAlbumRow() {
        InstagramMedia media = InstagramMedia.create(
                "car001",
                "carousel",
                "CAROUSEL_ALBUM",
                "https://cdn.example/carousel.jpg",
                "https://www.instagram.com/p/car001",
                TAKEN_AT,
                1,
                Instant.now());

        when(repo.findAllByOrderByPositionAsc(any(Limit.class))).thenReturn(List.of(media));

        List<InstagramPost> posts = query().latestPosts(1);

        assertThat(posts.getFirst().mediaType())
                .isEqualTo(InstagramPost.MediaType.CAROUSEL_ALBUM);
    }

    @Test
    void returnsEmptyWhenCacheIsEmpty() {
        when(repo.findAllByOrderByPositionAsc(any(Limit.class))).thenReturn(List.of());

        assertThat(query().latestPosts(8)).isEmpty();
    }
}
