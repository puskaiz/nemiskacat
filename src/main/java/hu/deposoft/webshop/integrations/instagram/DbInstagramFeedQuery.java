package hu.deposoft.webshop.integrations.instagram;

import hu.deposoft.webshop.application.instagram.InstagramFeedQuery;
import hu.deposoft.webshop.application.instagram.InstagramPost;
import hu.deposoft.webshop.domain.instagram.InstagramMedia;
import hu.deposoft.webshop.domain.instagram.InstagramMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DB-backed {@link InstagramFeedQuery}: reads cached Instagram media rows
 * ordered by position ascending, up to {@code limit} items.
 * No live HTTP call — data is populated by the Instagram sync scheduler
 * (Task 2 + 3).
 */
@RequiredArgsConstructor
class DbInstagramFeedQuery implements InstagramFeedQuery {

    private final InstagramMediaRepository repo;

    @Override
    @Transactional(readOnly = true)
    public List<InstagramPost> latestPosts(int limit) {
        return repo.findAllByOrderByPositionAsc(Limit.of(limit))
                .stream()
                .map(this::toPost)
                .toList();
    }

    private InstagramPost toPost(InstagramMedia m) {
        return new InstagramPost(
                m.getId(),
                m.getCaption(),
                InstagramPost.MediaType.valueOf(m.getMediaType()),
                m.getDisplayUrl(),
                m.getPermalink(),
                m.getTakenAt());
    }
}
