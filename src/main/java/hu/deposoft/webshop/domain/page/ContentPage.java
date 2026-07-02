package hu.deposoft.webshop.domain.page;

import hu.deposoft.webshop.domain.blog.PublicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

/** DB-backed static content page; HTML body (no markdown). Mirrors BlogPost's shape. */
@Entity
@Table(name = "content_page")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentPage {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", unique = true)
    private Long externalId;

    @Column(nullable = false, unique = true)
    private String slug;

    @Setter
    @Column(nullable = false)
    private String title;

    @Setter
    @Column(name = "body_html", nullable = false)
    private String bodyHtml = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PublicationStatus status = PublicationStatus.DRAFT;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Setter
    @Column(name = "seo_title")
    private String seoTitle;

    @Setter
    @Column(name = "seo_description")
    private String seoDescription;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static boolean isValidSlug(String slug) {
        return slug != null && SLUG.matcher(slug).matches();
    }

    public static ContentPage create(Long externalId, String slug, String title) {
        if (!isValidSlug(slug)) {
            throw new IllegalArgumentException("Invalid content page slug: " + slug);
        }
        ContentPage p = new ContentPage();
        p.externalId = externalId;
        p.slug = slug;
        p.title = title;
        return p;
    }

    public void publish(OffsetDateTime now) {
        this.status = PublicationStatus.PUBLISHED;
        if (this.publishedAt == null) {
            this.publishedAt = now;
        }
    }

    public void unpublish() {
        this.status = PublicationStatus.DRAFT;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
