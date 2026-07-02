package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.catalog.Variant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Short checkout hold for flagged (single-piece) products. Expired rows are
 * ignored by the ledger; renewal updates {@link #expiresAt}.
 */
@Entity
@Table(name = "reservation", uniqueConstraints = @UniqueConstraint(columnNames = {"variant_id", "cart_token"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private Variant variant;

    @Column(name = "cart_token", nullable = false)
    private String cartToken;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static Reservation hold(Variant variant, String cartToken, int quantity, OffsetDateTime expiresAt) {
        Reservation r = new Reservation();
        r.variant = variant;
        r.cartToken = cartToken;
        r.quantity = quantity;
        r.expiresAt = expiresAt;
        return r;
    }

    public void renew(int quantity, OffsetDateTime expiresAt) {
        this.quantity = quantity;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
