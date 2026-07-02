package hu.deposoft.webshop.domain.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Product category. Self-referential tree via {@link #parent}. */
@Entity
@Table(name = "category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifier in the external source system the catalog was imported from. */
    @Column(name = "external_id")
    private Long externalId;

    /** Immutable once created (CLAUDE.md #7) — no setter on purpose. */
    @Column(nullable = false, unique = true)
    private String slug;

    @Setter
    @Column(nullable = false)
    private String name;

    @Setter
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Setter
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Setter
    private String description;

    @Setter
    @Column(name = "seo_title")
    private String seoTitle;

    @Setter
    @Column(name = "meta_description")
    private String metaDescription;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Category create(Long externalId, String slug, String name) {
        Category c = new Category();
        c.externalId = externalId;
        c.slug = slug;
        c.name = name;
        return c;
    }

    /**
     * Adopt a manually-created category (external_id NULL) into the imported set
     * when Woo later publishes a category with the same (unique) slug. Keeps the
     * Woo import idempotent — see {@code CatalogImporter.upsertCategories}.
     */
    public void assignExternalId(Long externalId) {
        this.externalId = externalId;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
