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
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * T24 phase 3: after payment, an order's lines are invoiced per source —
 * Billingo issues the workshop lines, Kulcs-Soft receives the physical lines.
 * One Invoice row per (order, source); the step is idempotent and retries only
 * sources still in FAILED.
 */
@SpringBootTest
@Testcontainers
@Transactional
class InvoicingServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    @Autowired
    InvoicingService invoicing;

    @Autowired
    InvoiceRepository invoices;

    @Autowired
    CartService cart;

    @Autowired
    CheckoutService checkout;

    @Autowired
    CatalogImporter importer;

    @Autowired
    WorkshopService workshops;

    @MockitoBean
    InvoiceIssuer issuer;

    @MockitoBean
    OrderSink kulcsSink;

    @MockitoBean
    CreditNoteIssuer creditNoteIssuer;

    @BeforeEach
    void seedPhysicalCatalog() {
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(),
                List.of(paint), List.of()));
    }

    /** A paid order with one physical (KULCS_SOFT) and one workshop (BILLINGO) line. */
    private Order mixedOrder(String key) {
        Product ws = workshops.createWorkshop("Bútorfestés workshop", "butorfestes-" + key, "Egész nap", 27);
        workshops.addSession(ws, NOW.plusDays(7), 5, 15_000L, "WS-" + key);
        String token = cart.addItem(null, "FES-1", 1).token();
        cart.addItem(token, "WS-" + key, 1);
        return checkout.placeOrder(token, new PlaceOrderCommand(
                key, "Teszt Elek", "teszt@example.com", "+36201234567",
                "1111", "Budapest", "Fő utca 1.", null, "gls"));
    }

    @Test
    void issuesBillingoForWorkshopsAndPushesPhysicalToKulcs() {
        when(issuer.issue(any(), any()))
                .thenReturn(new InvoiceIssuer.InvoiceResult("WS-2026-1", "https://billingo/inv/1", "42"));
        Order order = mixedOrder("inv-a");

        invoicing.invoice(order.getId());

        var billingo = invoices.findByOrderAndSourceAndType(order, InvoiceSource.BILLINGO, InvoiceType.INVOICE).orElseThrow();
        assertThat(billingo.getState()).isEqualTo(InvoiceState.ISSUED);
        assertThat(billingo.getInvoiceNumber()).isEqualTo("WS-2026-1");
        assertThat(billingo.getPublicUrl()).isEqualTo("https://billingo/inv/1");

        var kulcs = invoices.findByOrderAndSourceAndType(order, InvoiceSource.KULCS_SOFT, InvoiceType.INVOICE).orElseThrow();
        assertThat(kulcs.getState()).isEqualTo(InvoiceState.PUSHED);

        // the issuer received only the workshop line, the sink only the physical line
        verify(issuer, times(1)).issue(any(), any());
        verify(kulcsSink, times(1)).push(any(), any());
    }

    @Test
    void isIdempotentForAlreadyInvoicedSources() {
        when(issuer.issue(any(), any()))
                .thenReturn(new InvoiceIssuer.InvoiceResult("WS-2026-2", "https://billingo/inv/2", "43"));
        Order order = mixedOrder("inv-b");

        invoicing.invoice(order.getId());
        invoicing.invoice(order.getId());

        assertThat(invoices.findByState(InvoiceState.ISSUED)).hasSize(1);
        // no second call to either port
        verify(issuer, times(1)).issue(any(), any());
        verify(kulcsSink, times(1)).push(any(), any());
        verifyNoMoreInteractions(issuer, kulcsSink);
    }

    @Test
    void retriesOnlyTheFailedSource() {
        when(issuer.issue(any(), any()))
                .thenThrow(new RuntimeException("Billingo unavailable"))
                .thenReturn(new InvoiceIssuer.InvoiceResult("WS-2026-3", "https://billingo/inv/3", "44"));
        Order order = mixedOrder("inv-c");

        invoicing.invoice(order.getId()); // Billingo fails, Kulcs succeeds
        var failed = invoices.findByOrderAndSourceAndType(order, InvoiceSource.BILLINGO, InvoiceType.INVOICE).orElseThrow();
        assertThat(failed.getState()).isEqualTo(InvoiceState.FAILED);
        assertThat(failed.getMessage()).contains("Billingo unavailable");

        invoicing.invoice(order.getId()); // retry: Billingo now succeeds, Kulcs untouched
        var fixed = invoices.findByOrderAndSourceAndType(order, InvoiceSource.BILLINGO, InvoiceType.INVOICE).orElseThrow();
        assertThat(fixed.getState()).isEqualTo(InvoiceState.ISSUED);
        assertThat(fixed.getInvoiceNumber()).isEqualTo("WS-2026-3");

        verify(issuer, times(2)).issue(any(), any()); // failed once, retried once
        verify(kulcsSink, times(1)).push(any(), any()); // pushed once, not retried
    }

    @Test
    void creditNoteForLineRecordsLineScopedRowForBillingoWorkshopLine() {
        when(creditNoteIssuer.creditNote(any(), any(), any()))
                .thenReturn(new InvoiceIssuer.InvoiceResult("ST-1", "https://billingo/st/1", "99"));
        Order order = mixedOrder("cn-line");
        hu.deposoft.webshop.domain.order.OrderItem wsLine = order.getItems().stream()
                .filter(i -> i.getInvoiceSource() == InvoiceSource.BILLINGO).findFirst().orElseThrow();

        invoicing.creditNoteForLine(wsLine);

        var note = invoices.findByOrderItemAndType(wsLine, InvoiceType.CREDIT_NOTE).orElseThrow();
        assertThat(note.getState()).isEqualTo(InvoiceState.ISSUED);
        assertThat(note.getOrderItem().getId()).isEqualTo(wsLine.getId());
        verify(creditNoteIssuer, times(1)).creditNote(any(), any(), any());
    }

    @Test
    void creditNoteForLineIsIdempotent() {
        when(creditNoteIssuer.creditNote(any(), any(), any()))
                .thenReturn(new InvoiceIssuer.InvoiceResult("ST-2", "u", "1"));
        Order order = mixedOrder("cn-idem");
        hu.deposoft.webshop.domain.order.OrderItem wsLine = order.getItems().stream()
                .filter(i -> i.getInvoiceSource() == InvoiceSource.BILLINGO).findFirst().orElseThrow();

        invoicing.creditNoteForLine(wsLine);
        invoicing.creditNoteForLine(wsLine);

        verify(creditNoteIssuer, times(1)).creditNote(any(), any(), any());
        var note = invoices.findByOrderItemAndType(wsLine, InvoiceType.CREDIT_NOTE).orElseThrow();
        assertThat(note.getState()).isEqualTo(InvoiceState.ISSUED);
    }

    @Test
    void creditNoteForLinePushesKulcsForPhysicalLine() {
        Order order = mixedOrder("cn-kulcs");
        OrderItem physical = order.getItems().stream()
                .filter(i -> i.getInvoiceSource() == InvoiceSource.KULCS_SOFT).findFirst().orElseThrow();

        invoicing.creditNoteForLine(physical);

        var note = invoices.findByOrderItemAndType(physical, InvoiceType.CREDIT_NOTE).orElseThrow();
        assertThat(note.getState()).isEqualTo(InvoiceState.PUSHED);
        assertThat(note.getOrderItem().getId()).isEqualTo(physical.getId());
        verify(kulcsSink, times(1)).pushCreditNote(any(), any());
    }
}
