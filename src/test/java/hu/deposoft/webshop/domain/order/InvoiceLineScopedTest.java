package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import hu.deposoft.webshop.domain.catalog.Product;
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

@SpringBootTest
@Testcontainers
@Transactional
class InvoiceLineScopedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired InvoiceRepository invoices;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired CatalogImporter importer;
    @Autowired WorkshopService workshops;

    @BeforeEach
    void seed() {
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint), List.of()));
    }

    @Test
    void twoLineScopedCreditNotesCoexistWithWholeOrderOne() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Product ws = workshops.createWorkshop("WS", "ws-x", "leiras", 27);
        workshops.addSession(ws, now.plusDays(7), 5, 15_000L, "WS-A");
        workshops.addSession(ws, now.plusDays(8), 5, 15_000L, "WS-B");
        String token = cart.addItem(null, "WS-A", 1).token();
        cart.addItem(token, "WS-B", 1);
        var order = checkout.placeOrder(token, new PlaceOrderCommand("ls", "Teszt",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "gls"));

        OrderItem lineA = order.getItems().get(0);
        OrderItem lineB = order.getItems().get(1);

        invoices.save(Invoice.creditNote(order, InvoiceSource.BILLINGO));            // whole-order, order_item NULL
        invoices.save(Invoice.creditNote(order, InvoiceSource.BILLINGO, lineA));     // line-scoped A
        invoices.save(Invoice.creditNote(order, InvoiceSource.BILLINGO, lineB));     // line-scoped B
        invoices.flush();

        assertThat(invoices.findByOrderItemAndType(lineA, InvoiceType.CREDIT_NOTE)).isPresent();
        assertThat(invoices.findByOrderItemAndType(lineB, InvoiceType.CREDIT_NOTE)).isPresent();
        assertThat(invoices.findByOrderAndSourceAndTypeAndOrderItemIsNull(order, InvoiceSource.BILLINGO, InvoiceType.CREDIT_NOTE))
                .isPresent(); // the whole-order one (order_item NULL)
    }
}
