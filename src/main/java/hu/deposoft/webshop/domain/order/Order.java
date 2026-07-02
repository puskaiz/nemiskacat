package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.checkout.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * A placed order. Prices/names are snapshots taken at placement; status changes
 * only through {@link #transitionTo} (the single state-machine gate).
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Client-generated idempotency key (unique) — replays return the same order. */
    @Column(name = "client_key", nullable = false, unique = true)
    private String clientKey;

    /** WooCommerce order id for imported orders; null for native orders. */
    @Column(name = "woo_order_id")
    private Long wooOrderId;

    /** Provenance marker: "WOO_IMPORT" for imported orders, null for native. */
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.NEW;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(nullable = false)
    private String postcode;

    @Column(nullable = false)
    private String city;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    private String note;

    @Column(name = "ship_method_code", nullable = false)
    private String shipMethodCode;

    @Column(name = "ship_method_name", nullable = false)
    private String shipMethodName;

    @Column(name = "ship_gross_huf", nullable = false)
    private long shipGrossHuf;

    @Column(name = "items_gross_huf", nullable = false)
    private long itemsGrossHuf;

    @Column(name = "total_gross_huf", nullable = false)
    private long totalGrossHuf;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Order place(String clientKey, String customerName, String email, String phone,
                              String postcode, String city, String addressLine, String note,
                              String shipMethodCode, String shipMethodName, long shipGrossHuf) {
        Order o = new Order();
        o.clientKey = clientKey;
        o.customerName = customerName;
        o.email = email;
        o.phone = phone;
        o.postcode = postcode;
        o.city = city;
        o.addressLine = addressLine;
        o.note = note;
        o.shipMethodCode = shipMethodCode;
        o.shipMethodName = shipMethodName;
        o.shipGrossHuf = shipGrossHuf;
        o.totalGrossHuf = shipGrossHuf;
        return o;
    }

    private static String orPlaceholder(String v) {
        return (v == null || v.isBlank()) ? "—" : v;
    }

    /**
     * Build a historical order imported from WooCommerce. Sets the FINAL status
     * directly (no state-machine replay), preserves the original placement date,
     * and stores Woo totals verbatim (coupons/fees make total != items + ship).
     * Fires no events and touches no stock.
     */
    public static Order imported(long wooOrderId, OrderStatus status, String customerName, String email,
                                 String phone, String postcode, String city, String addressLine, String note,
                                 String shipMethodName, long shipGrossHuf, long itemsGrossHuf,
                                 long totalGrossHuf, OffsetDateTime createdAt) {
        Order o = new Order();
        o.clientKey = "woo-" + wooOrderId;
        o.wooOrderId = wooOrderId;
        o.source = "WOO_IMPORT";
        o.status = status;
        o.customerName = orPlaceholder(customerName);
        o.email = orPlaceholder(email);
        o.phone = phone;
        o.postcode = orPlaceholder(postcode);
        o.city = orPlaceholder(city);
        o.addressLine = orPlaceholder(addressLine);
        o.note = note;
        o.shipMethodCode = "woo-import";
        o.shipMethodName = orPlaceholder(shipMethodName);
        o.shipGrossHuf = shipGrossHuf;
        o.itemsGrossHuf = itemsGrossHuf;
        o.totalGrossHuf = totalGrossHuf;
        o.createdAt = createdAt;
        return o;
    }

    /** Append an imported line without recomputing totals (Woo totals are authoritative). */
    public void addImportedItem(OrderItem item) {
        items.add(item);
    }

    public void addItem(OrderItem item) {
        items.add(item);
        itemsGrossHuf += item.getLineGrossHuf();
        totalGrossHuf = itemsGrossHuf + shipGrossHuf;
    }

    public void transitionTo(OrderStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Illegal order transition %s -> %s".formatted(status, target));
        }
        status = target;
    }

    /** Display order number, e.g. NK-1024. */
    public String orderNumber() {
        return "NK-" + id;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
