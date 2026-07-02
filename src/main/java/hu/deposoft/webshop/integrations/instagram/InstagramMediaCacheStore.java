package hu.deposoft.webshop.integrations.instagram;

import hu.deposoft.webshop.domain.instagram.InstagramMedia;
import hu.deposoft.webshop.domain.instagram.InstagramMediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Thin transactional adapter whose sole job is the atomic cache-replace:
 * delete all existing rows then insert the fresh batch in a single transaction.
 *
 * <p>Because this is a separate Spring-managed bean (not a method on the
 * caller), the call crosses the AOP proxy boundary and {@link Transactional}
 * is honoured on every code path — including the scheduler-triggered one.
 *
 * <p>This class intentionally has <em>no</em> try/catch: exceptions must
 * propagate so the transaction rolls back, leaving the prior cache intact
 * rather than committing an empty table.
 */
@RequiredArgsConstructor
public class InstagramMediaCacheStore {

    private final InstagramMediaRepository mediaRepo;

    /**
     * Atomically replaces the entire {@code instagram_media} table with
     * {@code posts}.  Runs inside a single transaction; any persistence
     * failure rolls back the delete, preserving the prior cache.
     *
     * @param posts the new rows to store; must not be {@code null}
     */
    @Transactional
    public void replaceAll(List<InstagramMedia> posts) {
        mediaRepo.deleteAll();
        mediaRepo.saveAll(posts);
    }
}
