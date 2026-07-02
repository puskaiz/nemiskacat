package hu.deposoft.webshop.integrations.instagram;

import hu.deposoft.webshop.domain.instagram.InstagramMedia;
import hu.deposoft.webshop.domain.instagram.InstagramMediaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testcontainers integration test proving that {@link InstagramMediaCacheStore#replaceAll}
 * is truly atomic through real Spring wiring (AOP proxy + JPA transaction).
 *
 * <p>This test is intentionally NOT annotated {@code @Transactional}: wrapping
 * the whole test in a transaction would hide rollback behaviour because both the
 * method-under-test and the verification queries would share the same connection
 * and see the same intermediate state.  Instead each test manages its own data
 * and cleans up in {@code @AfterEach}.
 *
 * <p>Atomicity guarantee under test:
 * <ul>
 *   <li>Happy path: {@code replaceAll} deletes prior rows and inserts new ones
 *       in a single commit.</li>
 *   <li>Rollback path: when the insert batch fails (a {@code null} in a
 *       {@code NOT NULL} column forces a {@code DataIntegrityViolationException}
 *       at flush time), the transaction rolls back — the prior rows survive
 *       intact, proving the {@code deleteAll()} was also rolled back.</li>
 * </ul>
 *
 * <p>{@code instagram.enabled=true} is required so that
 * {@link InstagramIntegrationConfig.Enabled} registers the
 * {@link InstagramMediaCacheStore} bean.  The other enabled-only beans
 * ({@link InstagramGraphClient}, {@link InstagramSyncService}) are not called
 * by this test; they are wired but remain unused.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "instagram.enabled=true",
        "instagram.user-id=test-user",
        "instagram.access-token=test-token"
})
class InstagramMediaCacheStoreIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    InstagramMediaCacheStore cacheStore;

    @Autowired
    InstagramMediaRepository mediaRepo;

    @AfterEach
    void cleanup() {
        mediaRepo.deleteAll();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private InstagramMedia media(String id, int position) {
        return InstagramMedia.create(
                id,
                "Caption " + id,
                "IMAGE",
                "https://example.com/" + id + ".jpg",
                "https://www.instagram.com/p/" + id,
                Instant.parse("2024-01-01T12:00:00Z"),
                position,
                Instant.parse("2024-06-01T10:00:00Z"));
    }

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    void replaceAll_happyPath_tableContainsExactlyNewRows() {
        // Seed some prior rows.
        mediaRepo.saveAll(List.of(media("old1", 0), media("old2", 1)));

        List<InstagramMedia> newRows = List.of(
                media("new1", 0),
                media("new2", 1),
                media("new3", 2));

        cacheStore.replaceAll(newRows);

        List<InstagramMedia> stored = mediaRepo.findAll();
        assertThat(stored).extracting(InstagramMedia::getId)
                .containsExactlyInAnyOrder("new1", "new2", "new3");
        // old rows must be gone
        assertThat(stored).extracting(InstagramMedia::getId)
                .doesNotContain("old1", "old2");
    }

    @Test
    void replaceAll_happyPath_emptyListClearsTable() {
        mediaRepo.saveAll(List.of(media("x", 0), media("y", 1)));

        cacheStore.replaceAll(List.of());

        assertThat(mediaRepo.findAll()).isEmpty();
    }

    // ── rollback path ─────────────────────────────────────────────────────────

    /**
     * Failure is engineered by including an entity whose {@code mediaType} column
     * is {@code null}, violating the {@code NOT NULL} DB constraint.  Spring Data
     * JPA's {@code saveAll()} calls Hibernate's {@code merge()} per entity; no
     * Bean Validation annotations guard {@code InstagramMedia}, so the null passes
     * Hibernate's in-memory checks and reaches Postgres during the flush step —
     * inside the open transaction.  Postgres rejects it and Hibernate wraps the
     * SQL exception as a {@link DataIntegrityViolationException}.
     *
     * <p>Because there is no catch inside {@code replaceAll()}, the exception
     * propagates out and Spring rolls back the entire transaction — including the
     * preceding {@code deleteAll()}.  The prior rows survive.
     *
     * <p>Why not duplicate PK: Spring Data JPA calls {@code merge()} (not
     * {@code persist()}) for entities with a non-null String {@code @Id}, so two
     * objects sharing the same PK are silently merged (the second one overwrites
     * the first in the persistence context), producing no constraint violation.
     */
    @Test
    void replaceAll_persistFailure_rollsBackDeleteAndPreservesOldRows() {
        // Seed the prior cache.
        List<InstagramMedia> prior = List.of(media("prior1", 0), media("prior2", 1));
        mediaRepo.saveAll(prior);

        // A batch where one entity has null mediaType — NOT NULL in the schema.
        // This passes Hibernate's in-memory check (no @NotNull annotation) but
        // fails at Postgres flush time with a NOT NULL constraint violation.
        InstagramMedia bad = InstagramMedia.create(
                "bad-id",
                "caption",
                null,   // ← mediaType is NOT NULL in DB — will fail at flush
                "https://example.com/bad.jpg",
                "https://www.instagram.com/p/bad-id",
                Instant.parse("2024-01-01T12:00:00Z"),
                0,
                Instant.parse("2024-06-01T10:00:00Z"));

        List<InstagramMedia> badBatch = List.of(media("ok-id", 1), bad);

        assertThatThrownBy(() -> cacheStore.replaceAll(badBatch))
                .isInstanceOf(DataIntegrityViolationException.class);

        // After rollback the prior rows must still be in the table.
        List<String> surviving = mediaRepo.findAll().stream()
                .map(InstagramMedia::getId)
                .toList();
        assertThat(surviving)
                .as("prior rows must survive after the rolled-back replaceAll")
                .containsExactlyInAnyOrder("prior1", "prior2");
    }
}
