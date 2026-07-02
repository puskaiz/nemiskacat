package hu.deposoft.webshop.integrations.instagram;

import hu.deposoft.webshop.application.instagram.InstagramFeedQuery;
import hu.deposoft.webshop.domain.instagram.InstagramMediaRepository;
import hu.deposoft.webshop.domain.instagram.InstagramTokenRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Wires the Instagram feed integration behind the {@link InstagramFeedQuery} port.
 * Property-based conditions (not bean-based): mirrors the pattern used by
 * {@code KhposIntegrationConfig}.
 *
 * <p>With {@code instagram.enabled=false} (the default), the context still
 * provides an {@link InstagramFeedQuery} bean (the no-op stub) so the rest of
 * the application does not need to know whether the feature is active.
 *
 * <p>With {@code instagram.enabled=true}, also registers the HTTP client and
 * sync service. The scheduler that invokes the service lives in
 * {@code InstagramSyncScheduler} (itself conditional on the same property).
 */
@Configuration
public class InstagramIntegrationConfig {

    @Configuration
    @ConditionalOnProperty(name = "instagram.enabled", havingValue = "true")
    static class Enabled {

        @Bean
        InstagramFeedQuery dbInstagramFeedQuery(InstagramMediaRepository repo) {
            return new DbInstagramFeedQuery(repo);
        }

        @Bean
        InstagramGraphClient instagramGraphClient(InstagramProperties properties) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(properties.connectTimeout());
            factory.setReadTimeout(properties.readTimeout());
            return new InstagramGraphClient(
                    properties.graphBaseUrl(),
                    RestClient.builder().requestFactory(factory));
        }

        @Bean
        InstagramMediaCacheStore instagramMediaCacheStore(InstagramMediaRepository mediaRepo) {
            return new InstagramMediaCacheStore(mediaRepo);
        }

        @Bean
        InstagramSyncService instagramSyncService(
                InstagramGraphClient client,
                InstagramMediaCacheStore cacheStore,
                InstagramTokenRepository tokenRepo,
                InstagramProperties properties,
                Clock clock) {
            return new InstagramSyncService(client, cacheStore, tokenRepo, properties, clock);
        }
    }

    @Bean
    @ConditionalOnProperty(name = "instagram.enabled", havingValue = "false", matchIfMissing = true)
    InstagramFeedQuery disabledInstagramFeedQuery() {
        return new DisabledInstagramFeedQuery();
    }

    /**
     * Provides UTC system clock; tests override with {@code Clock.fixed(...)}.
     * Declared here so it is available whenever the enabled inner config is active,
     * and suppressed (no-op) when the feature is off.
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    @ConditionalOnProperty(name = "instagram.enabled", havingValue = "true")
    Clock instagramClock() {
        return Clock.systemUTC();
    }
}
