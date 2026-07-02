package hu.deposoft.webshop.integrations.instagram;

import hu.deposoft.webshop.application.instagram.InstagramFeedQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts the context boots with instagram.enabled=false (the default) and
 * that an {@link InstagramFeedQuery} bean is present, returning an empty list.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "instagram.enabled=false")
class InstagramContextBootsDisabledTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    InstagramFeedQuery feedQuery;

    @Test
    void contextLoadsWithFeatureOff() {
        // context must boot — test fails if Spring cannot wire the app
    }

    @Test
    void feedQueryBeanIsPresent() {
        assertThat(feedQuery).isNotNull();
    }

    @Test
    void feedQueryReturnsEmptyWhenFeatureDisabled() {
        assertThat(feedQuery.latestPosts(10)).isEmpty();
    }
}
