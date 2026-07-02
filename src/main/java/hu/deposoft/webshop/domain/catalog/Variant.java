package hu.deposoft.webshop.domain.catalog;

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
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Sellable unit. Every product has at least one variant (a simple product has one
 * default variant). Stock and price live here. Raw quantities never leave the
 * service layer; status is derived via {@link StockStatusCalculator}.
 */
@Entity
@Table(name = "variant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Variant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Identifier in the external source system; null for a synthetic default variant. */
    @Column(name = "external_id")
    private Long externalId;

    @Setter
    private String sku;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Setter
    @Column(name = "regular_price_huf")
    private Long regularPriceHuf;

    @Setter
    @Column(name = "sale_price_huf")
    private Long salePriceHuf;

    @Setter
    @Column(name = "sale_from")
    private OffsetDateTime saleFrom;

    @Setter
    @Column(name = "sale_to")
    private OffsetDateTime saleTo;

    @Setter
    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Setter
    @Column(nullable = false)
    private int position;

    @Setter
    @Column(name = "manage_stock", nullable = false)
    private boolean manageStock = true;

    @Column(name = "last_sync_qty")
    private Integer lastSyncQty;

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "manual_availability")
    private ManualAvailability manualAvailability;

    @Setter
    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    @ManyToMany
    @JoinTable(name = "variant_attribute_value",
            joinColumns = @JoinColumn(name = "variant_id"),
            inverseJoinColumns = @JoinColumn(name = "attribute_value_id"))
    private Set<AttributeValue> attributeValues = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Variant create(Product product, Long externalId, boolean isDefault) {
        Variant v = new Variant();
        v.product = product;
        v.externalId = externalId;
        v.isDefault = isDefault;
        return v;
    }

    /** Regular (list) price, or null if unpriced. */
    public Money regularPrice() {
        return regularPriceHuf == null ? null : Money.huf(regularPriceHuf);
    }

    /** Configured sale price, or null if none. Validity window is applied by the service layer. */
    public Money salePrice() {
        return salePriceHuf == null ? null : Money.huf(salePriceHuf);
    }

    /** Records the stock level seen at sync/import time (the ledger baseline, TERV §3.7). */
    public void recordSyncedStock(Integer qty, OffsetDateTime at) {
        this.lastSyncQty = qty;
        this.lastSyncAt = at;
    }

    public void replaceAttributeValues(Set<AttributeValue> values) {
        attributeValues.clear();
        attributeValues.addAll(values);
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
