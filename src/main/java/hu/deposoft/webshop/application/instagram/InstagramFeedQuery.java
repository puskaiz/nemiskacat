package hu.deposoft.webshop.application.instagram;

import java.util.List;

/**
 * Port: read the cached Instagram feed from storage.
 * Implementations: {@code DbInstagramFeedQuery} (enabled) and
 * {@code DisabledInstagramFeedQuery} (off by default).
 */
public interface InstagramFeedQuery {

    /**
     * Returns up to {@code limit} posts ordered by display position ascending.
     * Returns an empty list when the feature is disabled or the cache is empty.
     */
    List<InstagramPost> latestPosts(int limit);
}
