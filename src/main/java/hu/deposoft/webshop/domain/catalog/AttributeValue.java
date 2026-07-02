package hu.deposoft.webshop.domain.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A concrete value of an {@link Attribute} (e.g. "Pure White" for szín). */
@Entity
@Table(name = "attribute_value",
        uniqueConstraints = @UniqueConstraint(columnNames = {"attribute_id", "slug"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "attribute_id", nullable = false)
    private Attribute attribute;

    @Column(nullable = false)
    private String slug;

    @Setter
    @Column(nullable = false)
    private String label;

    @Setter
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public static AttributeValue create(Attribute attribute, String slug, String label, int sortOrder) {
        AttributeValue v = new AttributeValue();
        v.attribute = attribute;
        v.slug = slug;
        v.label = label;
        v.sortOrder = sortOrder;
        return v;
    }
}
