package hu.deposoft.webshop.integrations.instagram;

import hu.deposoft.webshop.application.instagram.InstagramFeedQuery;
import hu.deposoft.webshop.application.instagram.InstagramPost;

import java.util.List;

/**
 * No-op implementation of {@link InstagramFeedQuery} used when
 * {@code instagram.enabled=false} (the default). Always returns an empty list
 * so the rest of the application can depend on the port without knowing whether
 * the feature is on or off.
 */
class DisabledInstagramFeedQuery implements InstagramFeedQuery {

    @Override
    public List<InstagramPost> latestPosts(int limit) {
        return List.of();
    }
}
