package hu.deposoft.webshop.domain.blog;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Blog post. Markdown body; status drives public visibility (CLAUDE.md #9). */
@Entity
@Table(name = "blog_post")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlogPost {

    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Setter
    @Column(nullable = false)
    private String title;

    @Setter
    private String excerpt;

    @Setter
    @Column(name = "body_markdown", nullable = false)
    private String bodyMarkdown = "";

    @Setter
    @Column(name = "body_html", nullable = false)
    private String bodyHtml = "";

    @Setter
    @Column(name = "cover_image_key")
    private String coverImageKey;

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

    /**
     * Managed blog categories. The setter replaces the whole collection reference;
     * prefer {@code getCategories().clear()} + {@code addAll(...)} in the service layer
     * so Hibernate keeps tracking the same managed collection instance.
     */
    @Setter
    @ManyToMany
    @JoinTable(name = "blog_post_category",
            joinColumns = @JoinColumn(name = "blog_post_id"),
            inverseJoinColumns = @JoinColumn(name = "blog_category_id"))
    private Set<BlogCategory> categories = new LinkedHashSet<>();

    @Setter
    @ManyToMany
    @JoinTable(name = "blog_post_tag",
            joinColumns = @JoinColumn(name = "blog_post_id"),
            inverseJoinColumns = @JoinColumn(name = "blog_tag_id"))
    private Set<BlogTag> tags = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "blog_post_product", joinColumns = @JoinColumn(name = "blog_post_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "sku", nullable = false)
    private List<String> recommendedSkus = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static boolean isValidSlug(String slug) {
        return slug != null && SLUG.matcher(slug).matches();
    }

    public static BlogPost create(String slug, String title) {
        if (!isValidSlug(slug)) {
            throw new IllegalArgumentException("Invalid blog post slug: " + slug);
        }
        BlogPost p = new BlogPost();
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

    public void setRecommendedSkus(List<String> skus) {
        this.recommendedSkus = new ArrayList<>(skus);
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
