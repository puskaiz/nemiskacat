package hu.deposoft.webshop.domain.instagram;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Cached Instagram media row. The {@code id} is the IG media id (text PK).
 * Fields are deliberately flat (no lazy collections) so reads are safe outside
 * a transaction (OSIV is off).
 */
@Entity
@Table(name = "instagram_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstagramMedia {

    @Id
    private String id;

    @Column(nullable = true)
    private String caption;

    @Column(name = "media_type", nullable = false)
    private String mediaType;

    @Column(name = "display_url", nullable = false)
    private String displayUrl;

    @Column(nullable = false)
    private String permalink;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    @Column(nullable = false)
    private int position;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    public static InstagramMedia create(
            String id,
            String caption,
            String mediaType,
            String displayUrl,
            String permalink,
            Instant takenAt,
            int position,
            Instant fetchedAt) {
        InstagramMedia m = new InstagramMedia();
        m.id = id;
        m.caption = caption;
        m.mediaType = mediaType;
        m.displayUrl = displayUrl;
        m.permalink = permalink;
        m.takenAt = takenAt;
        m.position = position;
        m.fetchedAt = fetchedAt;
        return m;
    }
}
