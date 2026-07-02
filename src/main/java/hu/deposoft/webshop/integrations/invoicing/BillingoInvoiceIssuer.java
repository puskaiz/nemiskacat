package hu.deposoft.webshop.integrations.invoicing;

import hu.deposoft.billingo.client.DocumentClient;
import hu.deposoft.billingo.model.common.Currency;
import hu.deposoft.billingo.model.common.DocumentLanguage;
import hu.deposoft.billingo.model.common.PaymentMethod;
import hu.deposoft.billingo.model.common.UnitPriceType;
import hu.deposoft.billingo.model.common.Vat;
import hu.deposoft.billingo.model.document.Document;
import hu.deposoft.billingo.model.document.DocumentInsert;
import hu.deposoft.billingo.model.document.DocumentItem;
import hu.deposoft.billingo.model.document.DocumentPartner;
import hu.deposoft.billingo.model.partner.PartnerAddress;
import hu.deposoft.webshop.application.invoicing.InvoiceIssuer;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Issues a Billingo invoice for the workshop (BILLINGO) lines of a paid order
 * (T24). Active only when {@code webshop.invoicing.billingo-enabled=true} (see
 * {@link InvoicingConfig}); otherwise {@link DisabledInvoiceIssuer} stands in.
 *
 * <p>The order is already paid via KHPos, so the document is marked paid by bank
 * card. Line prices are gross HUF (our stored {@code unitGrossHuf}); VAT comes
 * from each line's snapshotted rate.
 */
@Slf4j
public class BillingoInvoiceIssuer implements InvoiceIssuer {

    private final DocumentClient documents;
    private final long blockId;

    public BillingoInvoiceIssuer(DocumentClient documents, long blockId) {
        this.documents = documents;
        this.blockId = blockId;
    }

    @Override
    public InvoiceResult issue(Order order, List<OrderItem> lines) {
        DocumentPartner partner = new DocumentPartner(
                order.getCustomerName(),
                new PartnerAddress("HU", order.getPostcode(), order.getCity(), order.getAddressLine()),
                order.getEmail() == null ? List.of() : List.of(order.getEmail()),
                null,   // private person — no tax code
                null);

        DocumentInsert.Builder doc = new DocumentInsert.Builder()
                .partner(partner)
                .blockId(blockId)
                .type("invoice")
                .fulfillmentDate(LocalDate.now())
                .dueDate(LocalDate.now())
                .paymentMethod(PaymentMethod.BANKCARD)
                .language(DocumentLanguage.HU)
                .currency(Currency.HUF)
                .electronic(true)
                .paid(true)
                .comment("Webshop rendelés: " + order.orderNumber());

        for (OrderItem line : lines) {
            doc.item(new DocumentItem.Builder()
                    .name(itemName(line))
                    .unitPrice(BigDecimal.valueOf(line.getUnitGrossHuf()))
                    .unitPriceType(UnitPriceType.GROSS)
                    .quantity(BigDecimal.valueOf(line.getQuantity()))
                    .unit("db")
                    .vat(vat(line.getTaxRatePercent()))
                    .build());
        }

        Document created = documents.create(doc.build());
        String publicUrl = safePublicUrl(created);
        log.info("Billingo invoice {} created for order {}", created.invoiceNumber(), order.orderNumber());
        return new InvoiceResult(created.invoiceNumber(), publicUrl, String.valueOf(created.id()));
    }

    private String itemName(OrderItem line) {
        if (line.getVariantLabel() != null && !line.getVariantLabel().isBlank()) {
            return line.getProductName() + " – " + line.getVariantLabel();
        }
        return line.getProductName();
    }

    /** Fetch the public URL separately; never let it fail the whole issue. */
    private String safePublicUrl(Document created) {
        if (created.publicUrl() != null) {
            return created.publicUrl();
        }
        try {
            return documents.getPublicUrl(created.id());
        } catch (RuntimeException e) {
            log.warn("Could not fetch public URL for Billingo invoice {}: {}",
                    created.invoiceNumber(), e.getMessage());
            return null;
        }
    }

    private Vat vat(int percent) {
        return switch (percent) {
            case 0 -> Vat.ZERO;
            case 5 -> Vat.FIVE;
            case 18 -> Vat.EIGHTEEN;
            case 27 -> Vat.TWENTY_SEVEN;
            default -> Vat.of(percent + "%");
        };
    }
}
