package hu.deposoft.webshop.integrations.instagram;

import hu.deposoft.webshop.application.instagram.InstagramPost;
import hu.deposoft.webshop.domain.instagram.InstagramMedia;
import hu.deposoft.webshop.domain.instagram.InstagramMediaRepository;
import hu.deposoft.webshop.domain.instagram.InstagramToken;
import hu.deposoft.webshop.domain.instagram.InstagramTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InstagramSyncService}.
 * All external dependencies mocked; time is a fixed Clock.
 *
 * <p>Note: these tests instantiate {@code InstagramSyncService} directly with
 * {@code new}, so they do NOT cover the AOP proxy / transactional boundary —
 * that is covered by {@link InstagramMediaCacheStoreIT}.  The atomic-replace
 * logic ({@code deleteAll} + {@code saveAll}) is now in
 * {@link InstagramMediaCacheStore} and verified separately.
 */
@ExtendWith(MockitoExtension.class)
class InstagramSyncServiceTest {

    static final Instant FIXED_NOW = Instant.parse("2024-06-01T12:00:00Z");
    static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Mock
    InstagramGraphClient client;
    @Mock
    InstagramMediaCacheStore cacheStore;
    @Mock
    InstagramMediaRepository mediaRepo;   // kept for tests that verify token-side behaviour
    @Mock
    InstagramTokenRepository tokenRepo;

    InstagramSyncService service;

    InstagramProperties properties;

    @BeforeEach
    void setUp() {
        properties = new InstagramProperties(
                true,
                "user123",
                "seed-access-token",
                "https://graph.instagram.com",
                8,
                "PT30M",
                java.time.Duration.ofSeconds(5),
                java.time.Duration.ofSeconds(10));
        service = new InstagramSyncService(client, cacheStore, tokenRepo, properties, FIXED_CLOCK);
    }

    // ── syncMedia — successful replace ──────────────────────────────────────

    @Test
    void syncMedia_success_callsCacheStoreWithPostsInOrder() {
        InstagramToken token = InstagramToken.create("valid-token", FIXED_NOW.minusSeconds(3600));
        when(tokenRepo.findById(InstagramToken.SINGLETON_ID)).thenReturn(Optional.of(token));

        List<InstagramPost> fetched = List.of(
                post("id1", InstagramPost.MediaType.IMAGE),
                post("id2", InstagramPost.MediaType.VIDEO),
                post("id3", InstagramPost.MediaType.CAROUSEL_ALBUM));
        when(client.fetchMedia("user123", "valid-token", 8)).thenReturn(fetched);

        service.syncMedia();

        ArgumentCaptor<List<InstagramMedia>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(cacheStore).replaceAll(savedCaptor.capture());

        List<InstagramMedia> saved = savedCaptor.getValue();
        assertThat(saved).hasSize(3);

        // positions must be 0-based in response order
        assertThat(saved.get(0).getId()).isEqualTo("id1");
        assertThat(saved.get(0).getPosition()).isEqualTo(0);
        assertThat(saved.get(1).getId()).isEqualTo("id2");
        assertThat(saved.get(1).getPosition()).isEqualTo(1);
        assertThat(saved.get(2).getId()).isEqualTo("id3");
        assertThat(saved.get(2).getPosition()).isEqualTo(2);

        // fetchedAt must be the clock's now
        assertThat(saved.get(0).getFetchedAt()).isEqualTo(FIXED_NOW);

        // mediaType string must match the enum name
        assertThat(saved.get(0).getMediaType()).isEqualTo("IMAGE");
        assertThat(saved.get(1).getMediaType()).isEqualTo("VIDEO");
        assertThat(saved.get(2).getMediaType()).isEqualTo("CAROUSEL_ALBUM");
    }

    @Test
    void syncMedia_fetchFailure_leavesExistingRowsIntactAndDoesNotThrow() {
        InstagramToken token = InstagramToken.create("valid-token", FIXED_NOW.minusSeconds(3600));
        when(tokenRepo.findById(InstagramToken.SINGLETON_ID)).thenReturn(Optional.of(token));

        when(client.fetchMedia(anyString(), anyString(), anyInt()))
                .thenThrow(new InstagramApiException("API down"));

        assertThatCode(() -> service.syncMedia()).doesNotThrowAnyException();

        // must NOT have touched cacheStore (fetch failed before any replace)
        verify(cacheStore, never()).replaceAll(any());
    }

    @Test
    void syncMedia_cacheStoreFailure_doesNotThrow() {
        // Simulates a post-fetch persist failure (e.g. DataIntegrityViolationException).
        // cacheStore would roll back internally; syncMedia must still swallow the error.
        InstagramToken token = InstagramToken.create("valid-token", FIXED_NOW.minusSeconds(3600));
        when(tokenRepo.findById(InstagramToken.SINGLETON_ID)).thenReturn(Optional.of(token));

        List<InstagramPost> fetched = List.of(post("id1", InstagramPost.MediaType.IMAGE));
        when(client.fetchMedia(anyString(), anyString(), anyInt())).thenReturn(fetched);
        doThrow(new RuntimeException("DB failure")).when(cacheStore).replaceAll(any());

        assertThatCode(() -> service.syncMedia()).doesNotThrowAnyException();
    }

    @Test
    void syncMedia_noToken_logsWarnAndReturnsWithoutFetching() {
        when(tokenRepo.findById(InstagramToken.SINGLETON_ID)).thenReturn(Optional.empty());

        assertThatCode(() -> service.syncMedia()).doesNotThrowAnyException();

        verify(client, never()).fetchMedia(anyString(), anyString(), anyInt());
        verify(cacheStore, never()).replaceAll(any());
    }

    @Test
    void syncMedia_blankToken_logsWarnAndReturnsWithoutFetching() {
        InstagramToken blankToken = InstagramToken.create("", FIXED_NOW.minusSeconds(3600));
        when(tokenRepo.findById(InstagramToken.SINGLETON_ID)).thenReturn(Optional.of(blankToken));

        assertThatCode(() -> service.syncMedia()).doesNotThrowAnyException();

        verify(client, never()).fetchMedia(anyString(), anyString(), anyInt());
        verify(cacheStore, never()).replaceAll(any());
    }

    // ── refreshTokenIfNeeded ─────────────────────────────────────────────────

    @Test
    void refreshTokenIfNeeded_tokenWithinThreshold_callsRefreshAndPersists() {
        // expiresAt = now + 5 days → within the 10-day threshold
        Instant soonExpiry = FIXED_NOW.plusSeconds(5 * 86400L);
        InstagramToken token = InstagramToken.create("old-token", FIXED_NOW.minusSeconds(3600));
        token.setExpiresAt(soonExpiry);
        when(tokenRepo.findById(InstagramToken.SINGLETON_ID)).thenReturn(Optional.of(token));

        RefreshedToken refreshed = new RefreshedToken("new-token", FIXED_NOW.plusSeconds(60 * 86400L));
        when(client.refreshToken("old-token", FIXED_NOW)).thenReturn(refreshed);

        service.refreshTokenIfNeeded();

        verify(client).refreshToken("old-token", FIXED_NOW);
        // token entity was updated with the new values
        assertThat(token.getAccessToken()).isEqualTo("new-token");
        assertThat(token.getExpiresAt()).isEqualTo(refreshed.expiresAt());
        verify(tokenRepo).save(token);
    }

    @Test
    void refreshTokenIfNeeded_tokenNullExpiry_callsRefresh() {
        // expiresAt = null → unknown → must refresh
        InstagramToken token = InstagramToken.create("old-token", FIXED_NOW.minusSeconds(3600));
        // expiresAt is already null by default
        when(tokenRepo.findById(InstagramToken.SINGLETON_ID)).thenReturn(Optional.of(token));

        RefreshedToken refreshed = new RefreshedToken("new-token", FIXED_NOW.plusSeconds(60 * 86400L));
        when(client.refreshToken("old-token", FIXED_NOW)).thenReturn(refreshed);

        service.refreshTokenIfNeeded();

        verify(client).refreshToken("old-token", FIXED_NOW);
        verify(tokenRepo).save(token);
    }

    @Test
    void refreshTokenIfNeeded_tokenComfortablyValid_doesNotCallRefresh() {
        // expiresAt = now + 30 days → well beyond the 10-day threshold
        Instant farExpiry = FIXED_NOW.plusSeconds(30 * 86400L);
        InstagramToken token = InstagramToken.create("good-token", FIXED_NOW.minusSeconds(3600));
        token.setExpiresAt(farExpiry);
        when(tokenRepo.findById(InstagramToken.SINGLETON_ID)).thenReturn(Optional.of(token));

        service.refreshTokenIfNeeded();

        verify(client, never()).refreshToken(anyString(), any());
        verify(tokenRepo, never()).save(any());
    }

    @Test
    void refreshTokenIfNeeded_refreshFailure_keepsOldTokenAndDoesNotThrow() {
        Instant soonExpiry = FIXED_NOW.plusSeconds(3 * 86400L);
        InstagramToken token = InstagramToken.create("old-token", FIXED_NOW.minusSeconds(3600));
        token.setExpiresAt(soonExpiry);
        when(tokenRepo.findById(InstagramToken.SINGLETON_ID)).thenReturn(Optional.of(token));

        when(client.refreshToken(anyString(), any()))
                .thenThrow(new InstagramApiException("Token refresh failed"));

        assertThatCode(() -> service.refreshTokenIfNeeded()).doesNotThrowAnyException();

        // old token preserved — no save
        verify(tokenRepo, never()).save(any());
        assertThat(token.getAccessToken()).isEqualTo("old-token");
    }

    @Test
    void refreshTokenIfNeeded_tableEmpty_seedsFromPropertiesAndSaves() {
        when(tokenRepo.findById(InstagramToken.SINGLETON_ID)).thenReturn(Optional.empty());
        // seed token has null expiry → will also trigger refresh attempt — stub it to throw
        // so we can isolate just the seeding behavior (refresh failure is handled gracefully)
        when(client.refreshToken(eq("seed-access-token"), any()))
                .thenThrow(new InstagramApiException("not needed for seeding test"));

        assertThatCode(() -> service.refreshTokenIfNeeded()).doesNotThrowAnyException();

        // must have saved a new token seeded from properties
        ArgumentCaptor<InstagramToken> savedCaptor = ArgumentCaptor.forClass(InstagramToken.class);
        verify(tokenRepo).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().getAccessToken()).isEqualTo("seed-access-token");
    }

    // ── sync() — refresh failure does not abort media sync ───────────────────

    @Test
    void sync_refreshFailureDoesNotAbortMediaSync() {
        // refresh will fail
        InstagramToken token = InstagramToken.create("old-token", FIXED_NOW.minusSeconds(3600));
        // null expiry → tries refresh
        when(tokenRepo.findById(InstagramToken.SINGLETON_ID))
                .thenReturn(Optional.of(token));
        when(client.refreshToken(anyString(), any()))
                .thenThrow(new InstagramApiException("refresh down"));

        // fetchMedia should still be called with the old token
        List<InstagramPost> fetched = List.of(post("id1", InstagramPost.MediaType.IMAGE));
        when(client.fetchMedia("user123", "old-token", 8)).thenReturn(fetched);

        assertThatCode(() -> service.sync()).doesNotThrowAnyException();

        verify(client).fetchMedia("user123", "old-token", 8);
        verify(cacheStore).replaceAll(any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private InstagramPost post(String id, InstagramPost.MediaType type) {
        return new InstagramPost(
                id,
                "caption " + id,
                type,
                "https://cdn.example/" + id + ".jpg",
                "https://www.instagram.com/p/" + id,
                Instant.parse("2024-05-01T10:00:00Z"));
    }
}
