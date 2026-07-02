package hu.deposoft.webshop.domain.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** A global variation attribute (e.g. szín, kiszerelés). */
@Entity
@Table(name = "attribute")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifier in the external source system the catalog was imported from. */
    @Column(name = "external_id")
    private Long externalId;

    @Column(nullable = false, unique = true)
    private String slug;

    @Setter
    @Column(nullable = false)
    private String label;

    @Setter
    @Column(nullable = false)
    private String type;

    @OneToMany(mappedBy = "attribute", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<AttributeValue> values = new ArrayList<>();

    public static Attribute create(Long externalId, String slug, String label, String type) {
        Attribute a = new Attribute();
        a.externalId = externalId;
        a.slug = slug;
        a.label = label;
        a.type = type;
        return a;
    }
}
