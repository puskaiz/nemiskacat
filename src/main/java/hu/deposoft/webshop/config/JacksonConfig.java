package hu.deposoft.webshop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Declares the application's primary {@link ObjectMapper}.
 *
 * <p>The in-house KHPos and Billingo starters each register their own
 * {@code tools.jackson} mapper as a {@code @ConditionalOnMissingBean} fallback.
 * With both on the classpath — as in the {@code local} profile, where KHPos is
 * enabled — two mapper beans exist and Spring Boot's own auto-configured mapper
 * is suppressed, leaving plain {@code ObjectMapper} injections (e.g.
 * {@code CatalogQueryService}) ambiguous. A single primary application mapper
 * keeps those injections deterministic; the starters' beans stay wired into the
 * starters' own clients. Bare {@code JsonMapper.builder().build()} matches the
 * mapper the app has used all along (the starters build theirs the same way).
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    ObjectMapper objectMapper() {
        return JsonMapper.builder().build();
    }
}
