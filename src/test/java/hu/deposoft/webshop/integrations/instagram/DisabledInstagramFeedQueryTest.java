package hu.deposoft.webshop.integrations.instagram;

import hu.deposoft.webshop.application.instagram.InstagramFeedQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the no-op stub returned when instagram.enabled=false.
 */
class DisabledInstagramFeedQueryTest {

    private final InstagramFeedQuery query = new DisabledInstagramFeedQuery();

    @Test
    void returnsEmptyListForAnyLimit() {
        assertThat(query.latestPosts(10)).isEmpty();
        assertThat(query.latestPosts(0)).isEmpty();
        assertThat(query.latestPosts(1)).isEmpty();
    }
}
