package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.catalog.Variant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Order line with price/name snapshots taken at placement (price locking). */
@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne
    @JoinColumn(name = "variant_id")
    private Variant variant;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "variant_label")
    private String variantLabel;

    private String sku;

    @Column(name = "unit_gross_huf", nullable = false)
    private long unitGrossHuf;

    @Column(name = "tax_rate_percent", nullable = false)
    private int taxRatePercent;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "invoice_source", nullable = false)
    private hu.deposoft.webshop.domain.catalog.InvoiceSource invoiceSource =
            hu.deposoft.webshop.domain.catalog.InvoiceSource.KULCS_SOFT;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "cancelled_quantity", nullable = false)
    private int cancelledQuantity = 0;

    @Column(name = "line_gross_huf", nullable = false)
    private long lineGrossHuf;

    public static OrderItem create(Order order, Variant variant, String productName, String variantLabel,
                                   String sku, long unitGrossHuf, int taxRatePercent, int quantity,
                                   hu.deposoft.webshop.domain.catalog.InvoiceSource invoiceSource) {
        OrderItem i = new OrderItem();
        i.order = order;
        i.variant = variant;
        i.productName = productName;
        i.variantLabel = variantLabel;
        i.sku = sku;
        i.unitGrossHuf = unitGrossHuf;
        i.taxRatePercent = taxRatePercent;
        i.quantity = quantity;
        i.lineGrossHuf = unitGrossHuf * quantity;
        i.invoiceSource = invoiceSource;
        return i;
    }

    /** Cancel the whole line (2b-2). The seat frees up via the availability sum. */
    public void cancelWholeLine() {
        if (cancelledQuantity >= quantity) {
            throw new IllegalStateException("Line " + id + " is already fully cancelled");
        }
        this.cancelledQuantity = quantity;
    }

    /** Move this line to another seat variant of the same price (2b-3 reschedule). */
    public void moveToSeat(Variant targetSeat) {
        if (targetSeat.getRegularPriceHuf() != unitGrossHuf) {
            throw new IllegalStateException(
                    "Target seat price " + targetSeat.getRegularPriceHuf()
                            + " != line unit price " + unitGrossHuf);
        }
        this.variant = targetSeat;
        this.sku = targetSeat.getSku();
    }
}
