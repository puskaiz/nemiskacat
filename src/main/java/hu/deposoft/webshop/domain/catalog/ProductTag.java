package hu.deposoft.webshop.domain.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Product tag (migrated from WooCommerce product_tag). Keyed by Woo term id. */
@Entity
@Table(name = "product_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", unique = true)
    private Long externalId;

    /** Immutable once created (CLAUDE.md #7). */
    @Column(nullable = false, unique = true)
    private String slug;

    @Setter
    @Column(nullable = false)
    private String name;

    public static ProductTag create(Long externalId, String slug, String name) {
        ProductTag t = new ProductTag();
        t.externalId = externalId;
        t.slug = slug;
        t.name = name;
        return t;
    }

    /** Admin-created tag (no Woo term id). */
    public static ProductTag createManual(String slug, String name) {
        ProductTag t = new ProductTag();
        t.slug = slug;
        t.name = name;
        return t;
    }
}
