package hu.deposoft.webshop.config;

import hu.deposoft.webshop.integrations.instagram.InstagramSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Periodically triggers the Instagram feed cache sync.
 * Only active when {@code instagram.enabled=true}; no jobs run when the
 * feature is off. Matches the pattern of {@link PaymentRecheckScheduler}.
 */
@Configuration
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "instagram.enabled", havingValue = "true")
public class InstagramSyncScheduler {

    private final InstagramSyncService syncService;

    /**
     * Runs a full sync (token refresh + media replace) on a configurable interval.
     * The body is defensively wrapped; {@link InstagramSyncService#sync()} already
     * swallows exceptions, but a scheduler method must never let anything escape.
     */
    @Scheduled(
            fixedDelayString = "${instagram.refresh-interval:PT30M}",
            initialDelayString = "PT1M")
    public void syncInstagramFeed() {
        try {
            syncService.sync();
        } catch (Exception ex) {
            log.error("Unexpected error in InstagramSyncScheduler — sync skipped", ex);
        }
    }
}
