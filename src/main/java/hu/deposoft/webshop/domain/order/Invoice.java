package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * The invoicing outcome for one order + source + type (T24/2b-1). Whole-order
 * documents are unique per (order, source, type) with a NULL order_item; a
 * line-scoped credit note (2b-2) links to a single order_item and is unique
 * per (order_item, type).
 */
@Entity
@Table(name = "invoice")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceState state;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "public_url")
    private String publicUrl;

    @Column(name = "external_id")
    private String externalId;

    private String message;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Invoice of(Order order, InvoiceSource source) {
        Invoice i = new Invoice();
        i.order = order;
        i.source = source;
        i.type = InvoiceType.INVOICE;
        i.state = InvoiceState.FAILED;
        return i;
    }

    public static Invoice creditNote(Order order, InvoiceSource source) {
        Invoice i = new Invoice();
        i.order = order;
        i.source = source;
        i.type = InvoiceType.CREDIT_NOTE;
        i.state = InvoiceState.FAILED;
        return i;
    }

    public static Invoice creditNote(Order order, InvoiceSource source, OrderItem orderItem) {
        Invoice i = creditNote(order, source);
        i.orderItem = orderItem;
        return i;
    }

    public void recordIssued(String invoiceNumber, String publicUrl, String externalId) {
        this.state = InvoiceState.ISSUED;
        this.invoiceNumber = invoiceNumber;
        this.publicUrl = publicUrl;
        this.externalId = externalId;
        this.message = null;
    }

    public void recordPushed() {
        this.state = InvoiceState.PUSHED;
        this.message = null;
    }

    public void recordFailed(String message) {
        this.state = InvoiceState.FAILED;
        this.message = message;
    }

    public boolean isSuccessful() {
        return state.isSuccessful();
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
