package hu.deposoft.webshop.config;

import hu.deposoft.webshop.integrations.instagram.InstagramProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the default values of {@link InstagramProperties} when only
 * mandatory fields are supplied.
 *
 * <p>Uses {@link ApplicationContextRunner} so that NO datasource, JPA, or
 * Flyway context is started — the test is fully hermetic and never touches
 * the ambient local PostgreSQL instance.
 */
class InstagramPropertiesDefaultsTest {

    @Configuration
    @EnableConfigurationProperties(InstagramProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues("instagram.enabled=false");

    @Test
    void defaultGraphBaseUrlIsInstagramGraph() {
        runner.run(ctx -> {
            InstagramProperties props = ctx.getBean(InstagramProperties.class);
            assertThat(props.graphBaseUrl()).isEqualTo("https://graph.instagram.com");
        });
    }

    @Test
    void defaultFetchLimitIsEight() {
        runner.run(ctx -> {
            InstagramProperties props = ctx.getBean(InstagramProperties.class);
            assertThat(props.fetchLimit()).isEqualTo(8);
        });
    }

    @Test
    void defaultConnectTimeoutIsFiveSeconds() {
        runner.run(ctx -> {
            InstagramProperties props = ctx.getBean(InstagramProperties.class);
            assertThat(props.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    void defaultReadTimeoutIsTenSeconds() {
        runner.run(ctx -> {
            InstagramProperties props = ctx.getBean(InstagramProperties.class);
            assertThat(props.readTimeout()).isEqualTo(Duration.ofSeconds(10));
        });
    }
}
