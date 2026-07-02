package hu.deposoft.webshop.application.invoicing;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.order.InvoiceRepository;
import hu.deposoft.webshop.domain.order.InvoiceState;
import hu.deposoft.webshop.domain.order.InvoiceType;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Credit note routes by source like the forward invoicing: workshop lines →
 * Billingo (disabled here → recorded FAILED/pending), physical lines → Kulcs
 * (pushed). Idempotent. Default (test) profile keeps Billingo disabled.
 */
@SpringBootTest
@Testcontainers
@Transactional
class CreditNoteServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    @Autowired InvoicingService invoicing;
    @Autowired InvoiceRepository invoices;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired CatalogImporter importer;
    @Autowired WorkshopService workshops;

    @BeforeEach
    void seedPhysical() {
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint), List.of()));
    }

    private Order mixedOrder(String key) {
        Product ws = workshops.createWorkshop("WS", "ws-" + key, "x", 27);
        workshops.addSession(ws, NOW.plusDays(7), 5, 15_000L, "WS-" + key);
        String token = cart.addItem(null, "FES-1", 1).token();
        cart.addItem(token, "WS-" + key, 1);
        return checkout.placeOrder(token, new PlaceOrderCommand(key, "Teszt", "t@example.com", null,
                "1111", "Budapest", "Fő u. 1.", null, "pickup"));
    }

    @Test
    void creditNoteRoutesBySource() {
        Order order = mixedOrder("cn-a");

        invoicing.creditNote(order.getId());

        var billingo = invoices.findByOrderAndSourceAndType(order, InvoiceSource.BILLINGO, InvoiceType.CREDIT_NOTE).orElseThrow();
        var kulcs = invoices.findByOrderAndSourceAndType(order, InvoiceSource.KULCS_SOFT, InvoiceType.CREDIT_NOTE).orElseThrow();
        assertThat(billingo.getState()).isEqualTo(InvoiceState.FAILED);   // Billingo disabled → pending
        assertThat(kulcs.getState()).isEqualTo(InvoiceState.PUSHED);      // Kulcs stub → pushed
    }

    @Test
    void creditNoteIsIdempotentForSuccessfulSources() {
        Order order = mixedOrder("cn-b");
        invoicing.creditNote(order.getId());
        invoicing.creditNote(order.getId()); // second call must not duplicate the Kulcs (PUSHED) row

        assertThat(invoices.findByState(InvoiceState.PUSHED).stream()
                .filter(i -> i.getType() == InvoiceType.CREDIT_NOTE)
                .filter(i -> i.getOrder().getId().equals(order.getId()))).hasSize(1);
        assertThat(invoices.findByState(InvoiceState.FAILED).stream()
                .filter(i -> i.getType() == InvoiceType.CREDIT_NOTE)
                .filter(i -> i.getOrder().getId().equals(order.getId()))).hasSize(1);
    }

    @Test
    void retryFailedReattemptsBothInvoiceAndCreditNoteByType() {
        Order order = mixedOrder("cn-retry");
        invoicing.invoice(order.getId());     // BILLINGO INVOICE -> FAILED (disabled), KULCS INVOICE -> PUSHED
        invoicing.creditNote(order.getId());  // BILLINGO CREDIT_NOTE -> FAILED (disabled), KULCS CREDIT_NOTE -> PUSHED

        invoicing.retryFailed();              // must not throw; must re-attempt each FAILED row via the right method

        // the two BILLINGO rows are still FAILED and still correctly typed (no cross-routing, no duplicates)
        var inv = invoices.findByOrderAndSourceAndType(order, InvoiceSource.BILLINGO, InvoiceType.INVOICE).orElseThrow();
        var cn = invoices.findByOrderAndSourceAndType(order, InvoiceSource.BILLINGO, InvoiceType.CREDIT_NOTE).orElseThrow();
        assertThat(inv.getState()).isEqualTo(InvoiceState.FAILED);
        assertThat(cn.getState()).isEqualTo(InvoiceState.FAILED);
        // the PUSHED Kulcs rows are unchanged (one each)
        assertThat(invoices.findByOrderAndSourceAndType(order, InvoiceSource.KULCS_SOFT, InvoiceType.INVOICE).orElseThrow().getState()).isEqualTo(InvoiceState.PUSHED);
        assertThat(invoices.findByOrderAndSourceAndType(order, InvoiceSource.KULCS_SOFT, InvoiceType.CREDIT_NOTE).orElseThrow().getState()).isEqualTo(InvoiceState.PUSHED);
    }
}
