package hu.deposoft.webshop.domain.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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

/**
 * Catalog product. Carries identity (slug/external id), descriptive and SEO
 * fields, its categories, and one or more {@link Variant}s (the sellable units).
 */
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifier in the external source system the catalog was imported from. */
    @Column(name = "external_id")
    private Long externalId;

    /** Immutable once created for normal products; admin can update workshop slugs. */
    @Column(nullable = false, unique = true)
    private String slug;

    @Setter
    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType type;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductLifecycle lifecycle = ProductLifecycle.ACTIVE;

    @Setter
    @Column(name = "short_description")
    private String shortDescription;

    @Setter
    private String description;

    @Setter
    @Column(name = "tax_class")
    private String taxClass;

    @Setter
    @Column(name = "seo_title")
    private String seoTitle;

    @Setter
    @Column(name = "meta_description")
    private String metaDescription;

    /** Switchable 15-minute checkout hold for single-piece/unique items (TERV §3.7). */
    @Setter
    @Column(name = "reserve_on_checkout", nullable = false)
    private boolean reserveOnCheckout;

    /** Who invoices this product (T24): physical → Kulcs-Soft, workshop → Billingo. */
    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "invoice_source", nullable = false)
    private InvoiceSource invoiceSource = InvoiceSource.KULCS_SOFT;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "fulfilment_type", nullable = false)
    private FulfilmentType fulfilmentType = FulfilmentType.SHIP;

    /** Explicit VAT rate; when null the tax-class default applies. */
    @Setter
    @Column(name = "vat_rate_percent")
    private Integer vatRatePercent;

    @ManyToMany
    @JoinTable(name = "product_category",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private Set<Category> categories = new LinkedHashSet<>();

    @ManyToMany
    @JoinTable(name = "product_tag_map",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "product_tag_id"))
    private Set<ProductTag> tags = new LinkedHashSet<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<Variant> variants = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Product create(Long externalId, String slug, String name,
                                 ProductType type, ProductStatus status) {
        Product p = new Product();
        p.externalId = externalId;
        p.slug = slug;
        p.name = name;
        p.type = type;
        p.status = status;
        return p;
    }

    /** Admin-only: rename the slug of a hand-managed (non-imported) product. Imported
     *  products keep their Woo slug (SEO-stable URLs). */
    public void updateSlug(String newSlug) {
        if (externalId != null) {
            throw new IllegalStateException("Slug is immutable for imported product " + id);
        }
        this.slug = newSlug;
    }

    public boolean isDiscontinued() {
        return lifecycle == ProductLifecycle.DISCONTINUED;
    }

    public void replaceCategories(Set<Category> newCategories) {
        categories.clear();
        categories.addAll(newCategories);
    }

    public void replaceTags(Set<ProductTag> newTags) {
        tags.clear();
        tags.addAll(newTags);
    }

    public void addVariant(Variant variant) {
        variants.add(variant);
    }

    public void removeVariant(Variant variant) {
        variants.remove(variant);
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
