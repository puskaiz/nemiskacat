package hu.deposoft.webshop.integrations.instagram;

import hu.deposoft.webshop.application.instagram.InstagramPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class InstagramGraphClientTest {

    private static final String BASE_URL = "https://graph.instagram.com";

    private MockRestServiceServer server;
    private InstagramGraphClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new InstagramGraphClient(BASE_URL, builder);
    }

    // ── fetchMedia ───────────────────────────────────────────────────────────

    @Test
    void fetchMedia_success_mapsAllThreeMediaTypes() {
        server.expect(requestTo(containsString("/user123/media")))
              .andExpect(requestTo(containsString("fields=id,caption,media_type,media_url,thumbnail_url,permalink,timestamp")))
              .andExpect(requestTo(containsString("limit=3")))
              .andExpect(requestTo(containsString("access_token=tok")))
              .andRespond(withSuccess("""
                      {
                        "data": [
                          {
                            "id": "img1",
                            "caption": "Nice photo",
                            "media_type": "IMAGE",
                            "media_url": "https://cdn.example/img1.jpg",
                            "permalink": "https://www.instagram.com/p/img1",
                            "timestamp": "2024-05-01T12:00:00+0000"
                          },
                          {
                            "id": "vid2",
                            "media_type": "VIDEO",
                            "media_url": "https://cdn.example/vid2.mp4",
                            "thumbnail_url": "https://cdn.example/vid2_thumb.jpg",
                            "permalink": "https://www.instagram.com/p/vid2",
                            "timestamp": "2024-04-30T08:30:00+0000"
                          },
                          {
                            "id": "car3",
                            "caption": "Album",
                            "media_type": "CAROUSEL_ALBUM",
                            "media_url": "https://cdn.example/car3.jpg",
                            "permalink": "https://www.instagram.com/p/car3",
                            "timestamp": "2024-04-29T16:00:00+0000"
                          }
                        ]
                      }
                      """, MediaType.APPLICATION_JSON));

        List<InstagramPost> posts = client.fetchMedia("user123", "tok", 3);

        assertThat(posts).hasSize(3);

        // IMAGE — with caption
        InstagramPost img = posts.get(0);
        assertThat(img.id()).isEqualTo("img1");
        assertThat(img.caption()).isEqualTo("Nice photo");
        assertThat(img.mediaType()).isEqualTo(InstagramPost.MediaType.IMAGE);
        assertThat(img.displayUrl()).isEqualTo("https://cdn.example/img1.jpg");
        assertThat(img.permalink()).isEqualTo("https://www.instagram.com/p/img1");
        assertThat(img.timestamp()).isEqualTo(Instant.parse("2024-05-01T12:00:00Z"));

        // VIDEO — uses thumbnail_url, caption absent → null
        InstagramPost vid = posts.get(1);
        assertThat(vid.id()).isEqualTo("vid2");
        assertThat(vid.caption()).isNull();
        assertThat(vid.mediaType()).isEqualTo(InstagramPost.MediaType.VIDEO);
        assertThat(vid.displayUrl()).isEqualTo("https://cdn.example/vid2_thumb.jpg");
        assertThat(vid.timestamp()).isEqualTo(Instant.parse("2024-04-30T08:30:00Z"));

        // CAROUSEL_ALBUM — uses media_url
        InstagramPost car = posts.get(2);
        assertThat(car.id()).isEqualTo("car3");
        assertThat(car.mediaType()).isEqualTo(InstagramPost.MediaType.CAROUSEL_ALBUM);
        assertThat(car.displayUrl()).isEqualTo("https://cdn.example/car3.jpg");
        assertThat(car.timestamp()).isEqualTo(Instant.parse("2024-04-29T16:00:00Z"));

        server.verify();
    }

    @Test
    void fetchMedia_nonTwoXx_throwsInstagramApiException() {
        server.expect(requestTo(containsString("/user123/media")))
              .andRespond(withBadRequest().body("""
                      {"error":{"message":"Invalid token","type":"OAuthException","code":190}}
                      """).contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchMedia("user123", "bad_token", 5))
                .isInstanceOf(InstagramApiException.class)
                .hasMessageContaining("400");
    }

    @Test
    void fetchMedia_emptyDataArray_returnsEmptyList() {
        server.expect(requestTo(containsString("/user123/media")))
              .andRespond(withSuccess("""
                      {"data":[]}
                      """, MediaType.APPLICATION_JSON));

        List<InstagramPost> posts = client.fetchMedia("user123", "tok", 8);

        assertThat(posts).isEmpty();
        server.verify();
    }

    @Test
    void fetchMedia_unknownMediaType_skipsItemAndReturnsRest() {
        server.expect(requestTo(containsString("/user123/media")))
              .andRespond(withSuccess("""
                      {
                        "data": [
                          {
                            "id": "reel1",
                            "media_type": "REEL",
                            "media_url": "https://cdn.example/reel1.mp4",
                            "permalink": "https://www.instagram.com/p/reel1",
                            "timestamp": "2024-05-01T10:00:00+0000"
                          },
                          {
                            "id": "img2",
                            "caption": "keep me",
                            "media_type": "IMAGE",
                            "media_url": "https://cdn.example/img2.jpg",
                            "permalink": "https://www.instagram.com/p/img2",
                            "timestamp": "2024-05-01T09:00:00+0000"
                          }
                        ]
                      }
                      """, MediaType.APPLICATION_JSON));

        List<InstagramPost> posts = client.fetchMedia("user123", "tok", 8);

        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).id()).isEqualTo("img2");
        server.verify();
    }

    // ── scheme validation ────────────────────────────────────────────────────

    @Test
    void fetchMedia_nonHttpsPermalink_skipsItemAndReturnsRest() {
        server.expect(requestTo(containsString("/user123/media")))
              .andRespond(withSuccess("""
                      {
                        "data": [
                          {
                            "id": "bad1",
                            "media_type": "IMAGE",
                            "media_url": "https://cdn.example/bad1.jpg",
                            "permalink": "javascript:alert(1)",
                            "timestamp": "2024-05-01T10:00:00+0000"
                          },
                          {
                            "id": "good1",
                            "caption": "safe",
                            "media_type": "IMAGE",
                            "media_url": "https://cdn.example/good1.jpg",
                            "permalink": "https://www.instagram.com/p/good1",
                            "timestamp": "2024-05-01T09:00:00+0000"
                          }
                        ]
                      }
                      """, MediaType.APPLICATION_JSON));

        List<InstagramPost> posts = client.fetchMedia("user123", "tok", 8);

        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).id()).isEqualTo("good1");
        server.verify();
    }

    @Test
    void fetchMedia_httpDisplayUrl_skipsItemAndReturnsRest() {
        server.expect(requestTo(containsString("/user123/media")))
              .andRespond(withSuccess("""
                      {
                        "data": [
                          {
                            "id": "http1",
                            "media_type": "IMAGE",
                            "media_url": "http://cdn.example/http1.jpg",
                            "permalink": "https://www.instagram.com/p/http1",
                            "timestamp": "2024-05-01T10:00:00+0000"
                          },
                          {
                            "id": "good2",
                            "caption": "fine",
                            "media_type": "IMAGE",
                            "media_url": "https://cdn.example/good2.jpg",
                            "permalink": "https://www.instagram.com/p/good2",
                            "timestamp": "2024-05-01T09:00:00+0000"
                          }
                        ]
                      }
                      """, MediaType.APPLICATION_JSON));

        List<InstagramPost> posts = client.fetchMedia("user123", "tok", 8);

        assertThat(posts).hasSize(1);
        assertThat(posts.get(0).id()).isEqualTo("good2");
        server.verify();
    }

    // ── refreshToken ─────────────────────────────────────────────────────────

    @Test
    void refreshToken_success_computesExpiresAt() {
        server.expect(requestTo(containsString("/refresh_access_token")))
              .andExpect(requestTo(containsString("grant_type=ig_refresh_token")))
              .andExpect(requestTo(containsString("access_token=old_token")))
              .andRespond(withSuccess("""
                      {
                        "access_token": "new_long_lived_token",
                        "token_type": "bearer",
                        "expires_in": 5183944
                      }
                      """, MediaType.APPLICATION_JSON));

        Instant fixedNow = Instant.parse("2024-05-01T00:00:00Z");
        RefreshedToken result = client.refreshToken("old_token", fixedNow);

        assertThat(result.accessToken()).isEqualTo("new_long_lived_token");
        assertThat(result.expiresAt()).isEqualTo(fixedNow.plusSeconds(5183944));
        server.verify();
    }

    @Test
    void refreshToken_nonTwoXx_throwsInstagramApiException() {
        server.expect(requestTo(containsString("/refresh_access_token")))
              .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.refreshToken("expired_token", Instant.now()))
                .isInstanceOf(InstagramApiException.class)
                .hasMessageContaining("401");
    }

    @Test
    void fetchMedia_missingDataKey_throwsInstagramApiException() {
        server.expect(requestTo(containsString("/user123/media")))
              .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchMedia("user123", "tok", 5))
                .isInstanceOf(InstagramApiException.class)
                .hasMessageContaining("null");
    }

    @Test
    void refreshToken_missingAccessToken_throwsInstagramApiException() {
        server.expect(requestTo(containsString("/refresh_access_token")))
              .andRespond(withSuccess("""
                      {"token_type":"bearer","expires_in":100}
                      """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.refreshToken("old_token", Instant.now()))
                .isInstanceOf(InstagramApiException.class)
                .hasMessageContaining("null");
    }

    @Test
    void refreshToken_zeroExpiresIn_throwsInstagramApiException() {
        server.expect(requestTo(containsString("/refresh_access_token")))
              .andRespond(withSuccess("""
                      {"access_token":"new_token","token_type":"bearer","expires_in":0}
                      """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.refreshToken("old_token", Instant.now()))
                .isInstanceOf(InstagramApiException.class)
                .hasMessageContaining("null");
    }
}
