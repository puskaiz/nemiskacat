package hu.deposoft.webshop.integrations.instagram;

import hu.deposoft.webshop.application.instagram.InstagramPost;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin adapter over the Instagram Graph API. No persistence, no scheduling.
 * Constructed with a {@link RestClient.Builder} so tests can bind
 * {@code MockRestServiceServer} to the same builder instance.
 */
@Slf4j
public class InstagramGraphClient {

    private static final DateTimeFormatter IG_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private final String graphBaseUrl;
    private final RestClient restClient;

    public InstagramGraphClient(String graphBaseUrl, RestClient.Builder builder) {
        this.graphBaseUrl = graphBaseUrl;
        this.restClient = builder.build();
    }

    /**
     * Fetches media items for {@code userId}. Returns posts in API order (newest
     * first); the caller assigns positions.
     */
    public List<InstagramPost> fetchMedia(String userId, String accessToken, int limit) {
        MediaResponseDto response;
        try {
            response = restClient.get()
                    .uri(graphBaseUrl + "/{userId}/media"
                                 + "?fields=id,caption,media_type,media_url,thumbnail_url,permalink,timestamp"
                                 + "&limit={limit}&access_token={accessToken}",
                         userId, limit, accessToken)
                    .retrieve()
                    .body(MediaResponseDto.class);
        } catch (RestClientResponseException ex) {
            throw new InstagramApiException(
                    "Instagram fetchMedia failed: HTTP " + ex.getStatusCode(), ex);
        }

        if (response == null || response.data() == null) {
            throw new InstagramApiException("Instagram fetchMedia returned null body");
        }

        List<InstagramPost> posts = new ArrayList<>();
        for (MediaItemDto item : response.data()) {
            InstagramPost.MediaType mediaType;
            try {
                mediaType = InstagramPost.MediaType.valueOf(item.mediaType());
            } catch (IllegalArgumentException e) {
                log.warn("Skipping unknown Instagram media_type '{}' for id '{}'",
                         item.mediaType(), item.id());
                continue;
            }

            String displayUrl = resolveDisplayUrl(mediaType, item);

            if (!isHttpsUrl(item.permalink())) {
                log.warn("Skipping Instagram item with unsafe/missing permalink '{}' for id '{}'",
                         item.permalink(), item.id());
                continue;
            }
            if (!isHttpsUrl(displayUrl)) {
                log.warn("Skipping Instagram item with unsafe/missing displayUrl '{}' for id '{}'",
                         displayUrl, item.id());
                continue;
            }

            Instant timestamp = OffsetDateTime.parse(item.timestamp(), IG_TIMESTAMP).toInstant();

            posts.add(new InstagramPost(
                    item.id(),
                    item.caption(),
                    mediaType,
                    displayUrl,
                    item.permalink(),
                    timestamp));
        }
        return posts;
    }

    /**
     * Exchanges the current token for a refreshed long-lived token.
     *
     * @param now injected for deterministic expiry calculation in tests
     */
    public RefreshedToken refreshToken(String accessToken, Instant now) {
        RefreshResponseDto response;
        try {
            response = restClient.get()
                    .uri(graphBaseUrl + "/refresh_access_token"
                                 + "?grant_type=ig_refresh_token&access_token={token}",
                         accessToken)
                    .retrieve()
                    .body(RefreshResponseDto.class);
        } catch (RestClientResponseException ex) {
            throw new InstagramApiException(
                    "Instagram refreshToken failed: HTTP " + ex.getStatusCode(), ex);
        }

        if (response == null || response.accessToken() == null || response.expiresIn() <= 0) {
            throw new InstagramApiException("Instagram refreshToken returned null/empty body");
        }

        return new RefreshedToken(
                response.accessToken(),
                now.plusSeconds(response.expiresIn()));
    }

    // ── URL scheme validation ────────────────────────────────────────────────

    private static boolean isHttpsUrl(String url) {
        return url != null && !url.isBlank() && url.startsWith("https://");
    }

    // ── display-url selection ────────────────────────────────────────────────

    private static String resolveDisplayUrl(InstagramPost.MediaType type, MediaItemDto item) {
        if (type == InstagramPost.MediaType.VIDEO) {
            String thumb = item.thumbnailUrl();
            return (thumb != null && !thumb.isBlank()) ? thumb : item.mediaUrl();
        }
        return item.mediaUrl();
    }

    // ── DTOs (package-private for tests) ────────────────────────────────────

    @JsonNaming(tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
    record MediaItemDto(
            String id,
            String caption,
            String mediaType,
            String mediaUrl,
            String thumbnailUrl,
            String permalink,
            String timestamp) {
    }

    record MediaResponseDto(List<MediaItemDto> data) {}

    @JsonNaming(tools.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy.class)
    record RefreshResponseDto(String accessToken, long expiresIn) {}
}
