package hu.deposoft.webshop.integrations.instagram;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Configuration properties for the Instagram Basic Display / Graph API integration.
 * Secrets (userId, accessToken) must be supplied via environment variables; see
 * application.yml for the {@code ${ENV:}} placeholders.
 *
 * <p>Picked up automatically via {@code @ConfigurationPropertiesScan} on
 * {@link hu.deposoft.webshop.WebshopApplication}.
 */
@ConfigurationProperties("instagram")
public record InstagramProperties(
        boolean enabled,
        String userId,
        String accessToken,
        @DefaultValue("https://graph.instagram.com") String graphBaseUrl,
        @DefaultValue("8") int fetchLimit,
        @DefaultValue("PT30M") String refreshInterval,
        @DefaultValue("PT5S") Duration connectTimeout,
        @DefaultValue("PT10S") Duration readTimeout) {
}
