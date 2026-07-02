package hu.deposoft.webshop.domain.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Product image metadata. The binary lives in object storage under an immutable
 * hash key; the app never processes images (resizing = CDN optimizer URL params,
 * CLAUDE.md #8). Optionally bound to a specific {@link Variant}.
 */
@Entity
@Table(name = "product_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Setter
    @ManyToOne
    @JoinColumn(name = "variant_id")
    private Variant variant;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Setter
    private String alt;

    @Setter
    @Column(nullable = false)
    private int position;

    @Setter
    @Column(name = "is_featured", nullable = false)
    private boolean isFeatured;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static ProductImage create(Product product, String storageKey, String alt,
                                      int position, boolean featured) {
        ProductImage i = new ProductImage();
        i.product = product;
        i.storageKey = storageKey;
        i.alt = alt;
        i.position = position;
        i.isFeatured = featured;
        return i;
    }

    /** Repoint the image at a new storage key after its bytes are moved into local
     *  content-addressed storage (image backfill). The binary is otherwise immutable. */
    public void relocateStorage(String newStorageKey) {
        this.storageKey = newStorageKey;
    }

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
