package hu.deposoft.webshop.config;

import hu.deposoft.webshop.integrations.instagram.InstagramProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the {@code instagram.*} block binds correctly to
 * {@link InstagramProperties}.
 *
 * <p>Uses {@link ApplicationContextRunner} so that NO datasource, JPA, or
 * Flyway context is started — the test is fully hermetic and never touches
 * the ambient local PostgreSQL instance.
 */
class InstagramPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(InstagramProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void allFieldsBound() {
        runner.withPropertyValues(
                        "instagram.enabled=true",
                        "instagram.user-id=12345",
                        "instagram.access-token=TOKEN",
                        "instagram.graph-base-url=https://graph.example.com",
                        "instagram.fetch-limit=12",
                        "instagram.connect-timeout=PT7S",
                        "instagram.read-timeout=PT15S")
                .run(ctx -> {
                    InstagramProperties props = ctx.getBean(InstagramProperties.class);
                    assertThat(props.enabled()).isTrue();
                    assertThat(props.userId()).isEqualTo("12345");
                    assertThat(props.accessToken()).isEqualTo("TOKEN");
                    assertThat(props.graphBaseUrl()).isEqualTo("https://graph.example.com");
                    assertThat(props.fetchLimit()).isEqualTo(12);
                    assertThat(props.connectTimeout()).isEqualTo(Duration.ofSeconds(7));
                    assertThat(props.readTimeout()).isEqualTo(Duration.ofSeconds(15));
                });
    }
}
