package hu.deposoft.webshop.domain.cart;

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

/** One variant in a cart. No price stored — computed at read (locked at checkout, T9). */
@Entity
@Table(name = "cart_item", uniqueConstraints = @UniqueConstraint(columnNames = {"cart_id", "variant_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private Variant variant;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;

    public static CartItem create(Cart cart, Variant variant, int quantity) {
        CartItem i = new CartItem();
        i.cart = cart;
        i.variant = variant;
        i.quantity = quantity;
        return i;
    }

    public void changeQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, got " + quantity);
        }
        this.quantity = quantity;
    }

    public void increaseQuantity(int by) {
        changeQuantity(quantity + by);
    }

    @PrePersist
    void onCreate() {
        addedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
