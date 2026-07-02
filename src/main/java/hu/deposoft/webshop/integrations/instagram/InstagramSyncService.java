package hu.deposoft.webshop.integrations.instagram;

import hu.deposoft.webshop.application.instagram.InstagramPost;
import hu.deposoft.webshop.domain.instagram.InstagramMedia;
import hu.deposoft.webshop.domain.instagram.InstagramMediaRepository;
import hu.deposoft.webshop.domain.instagram.InstagramToken;
import hu.deposoft.webshop.domain.instagram.InstagramTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the Instagram feed cache:
 * <ol>
 *   <li>Refreshes the long-lived token when it is near expiry or unknown.</li>
 *   <li>Fetches the latest media and atomically replaces the cached rows.</li>
 * </ol>
 *
 * <p>No method throws out to the caller (graceful degradation): a failed
 * refresh keeps the old token; a failed fetch leaves the cache intact.
 * Both are suitable to be called directly from a {@code @Scheduled} method.
 *
 * <p>The atomic replace is delegated to {@link InstagramMediaCacheStore}, a
 * separate Spring bean, so its {@code @Transactional} boundary is honoured
 * even when called from {@link #sync()} (self-invocation would bypass the
 * AOP proxy).
 */
@Slf4j
@RequiredArgsConstructor
public class InstagramSyncService {

    /** Refresh the token when it expires within this window. */
    private static final Duration REFRESH_THRESHOLD = Duration.ofDays(10);

    private final InstagramGraphClient client;
    private final InstagramMediaCacheStore cacheStore;
    private final InstagramTokenRepository tokenRepo;
    private final InstagramProperties properties;
    private final Clock clock;

    // ── public API ───────────────────────────────────────────────────────────

    /**
     * Runs a full sync cycle: refresh token if needed, then replace media cache.
     * A failure in either step is swallowed and logged; the other step still runs.
     */
    public void sync() {
        refreshTokenIfNeeded();
        syncMedia();
    }

    /**
     * Refreshes the access token when it is null/unknown or within
     * {@link #REFRESH_THRESHOLD} of expiry. If the token table is empty, the
     * configured access token from properties is seeded first.
     *
     * <p>Never throws; a failed refresh logs at ERROR and preserves the old token.
     */
    public void refreshTokenIfNeeded() {
        try {
            Instant now = Instant.now(clock);

            InstagramToken token = loadOrSeedToken(now);

            Instant expiresAt = token.getExpiresAt();
            boolean needsRefresh = (expiresAt == null)
                    || expiresAt.isBefore(now.plus(REFRESH_THRESHOLD));

            if (!needsRefresh) {
                return;
            }

            log.debug("Instagram token needs refresh (expiresAt={}); calling API", expiresAt);
            try {
                RefreshedToken refreshed = client.refreshToken(token.getAccessToken(), now);
                token.update(refreshed.accessToken(), refreshed.expiresAt(), now);
                tokenRepo.save(token);
                log.info("Instagram token refreshed; new expiresAt={}", refreshed.expiresAt());
            } catch (Exception ex) {
                log.error("Instagram token refresh failed — keeping old token. Cause: {}", ex.getMessage(), ex);
                // old token preserved — do not rethrow
            }
        } catch (Exception ex) {
            log.error("Unexpected error in refreshTokenIfNeeded: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Fetches the latest media from the Instagram API and atomically replaces
     * the cached rows (delete-all + insert inside one transaction).
     *
     * <p>The fetch always happens BEFORE the delete so a failed fetch leaves the
     * existing cache intact. Never throws; errors are logged at WARN.
     *
     * <p>The atomic replace is performed by {@link InstagramMediaCacheStore#replaceAll},
     * a separate bean, so the transaction boundary is enforced via the AOP proxy.
     * If the persist step throws, {@code cacheStore} rolls back the delete; the
     * catch here runs after that rollback and logs without re-throwing.
     */
    public void syncMedia() {
        try {
            Optional<InstagramToken> tokenOpt =
                    tokenRepo.findById(InstagramToken.SINGLETON_ID);

            if (tokenOpt.isEmpty()) {
                log.warn("Instagram syncMedia: token table is empty — skipping fetch");
                return;
            }

            String accessToken = tokenOpt.get().getAccessToken();
            if (accessToken == null || accessToken.isBlank()) {
                log.warn("Instagram syncMedia: access token is blank — skipping fetch");
                return;
            }

            // Fetch FIRST — never delete until the API responds successfully.
            List<InstagramPost> posts =
                    client.fetchMedia(properties.userId(), accessToken, properties.fetchLimit());

            Instant now = Instant.now(clock);

            List<InstagramMedia> entities = new ArrayList<>(posts.size());
            for (int i = 0; i < posts.size(); i++) {
                InstagramPost p = posts.get(i);
                entities.add(InstagramMedia.create(
                        p.id(),
                        p.caption(),
                        p.mediaType().name(),
                        p.displayUrl(),
                        p.permalink(),
                        p.timestamp(),
                        i,          // 0-based position = API response order
                        now));
            }

            // Atomic replace: delegated to a separate bean so @Transactional applies.
            // If cacheStore.replaceAll throws, the transaction rolls back (delete is
            // undone) and the exception propagates here where it is caught and logged.
            cacheStore.replaceAll(entities);
            log.info("Instagram media cache replaced: {} posts stored", entities.size());

        } catch (InstagramApiException ex) {
            log.warn("Instagram syncMedia fetch failed — existing cache preserved. Cause: {}", ex.getMessage());
            // existing rows untouched; cacheStore rolled back any partial delete
        } catch (Exception ex) {
            log.warn("Unexpected error in Instagram syncMedia — existing cache preserved. Cause: {}", ex.getMessage(), ex);
        }
    }

    // ── private helpers ──────────────────────────────────────────────────────

    /**
     * Returns the existing token row, or creates and persists a seed token from
     * {@code InstagramProperties.accessToken()} when the table is empty.
     *
     * <p>No {@code @Transactional} here: this is a private method called by
     * {@code refreshTokenIfNeeded()} — Spring AOP ignores private and self-invoked
     * methods. {@code tokenRepo.save()} carries its own transaction (SimpleJpaRepository
     * is itself {@code @Transactional}).
     */
    private InstagramToken loadOrSeedToken(Instant now) {
        Optional<InstagramToken> existing = tokenRepo.findById(InstagramToken.SINGLETON_ID);
        if (existing.isPresent()) {
            return existing.get();
        }
        log.info("Instagram token table is empty — seeding from properties");
        InstagramToken seeded = InstagramToken.create(properties.accessToken(), now);
        tokenRepo.save(seeded);
        return seeded;
    }
}
