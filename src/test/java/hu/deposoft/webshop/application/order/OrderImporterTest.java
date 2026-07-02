package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.integrations.woo.SourceOrder;
import hu.deposoft.webshop.integrations.woo.SourceOrderItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class OrderImporterTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired OrderImporter importer;
    @Autowired OrderRepository orders;

    private SourceOrder order(long id, String status, boolean paid, String sku) {
        return new SourceOrder(id, "wc_order_" + id, status, "HUF",
                "2015-03-01T10:00:00Z", paid ? "2015-03-01T10:05:00Z" : null, 3768L,
                "Nagy Ágnes", "agi@example.com", "+36301", "1011", "Budapest", "Fő utca 1.", null,
                "Foxpost", 990, 7400, 8390, paid, paid ? "txn-" + id : null,
                List.of(new SourceOrderItem(55L, 56L, sku, "Régi termék", "1 kg", 2, 3700, 27, 7400)));
    }

    @Test
    void importsOrphanLineWithNullVariantAndStatus() {
        OrderImportReport report = importer.run(List.of(order(2001, "wc-completed", true, "GONE-SKU")));

        assertThat(report.imported()).isEqualTo(1);
        assertThat(report.orphanLines()).isEqualTo(1);
        assertThat(report.payments()).isEqualTo(1);
        Order saved = orders.findWithItemsByClientKey("woo-2001").orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(saved.getItems().getFirst().getVariant()).isNull();
        assertThat(saved.getItems().getFirst().getSku()).isEqualTo("GONE-SKU");
    }

    @Test
    void isIdempotentOnRerun() {
        importer.run(List.of(order(2002, "wc-completed", true, "GONE-SKU")));
        OrderImportReport second = importer.run(List.of(order(2002, "wc-completed", true, "GONE-SKU")));
        assertThat(second.imported()).isZero();
        assertThat(second.skipped()).isEqualTo(1);
        assertThat(orders.findByWooOrderId(2002L)).isPresent();
    }

    @Test
    void cancelledUnpaidGetsNoPayment() {
        OrderImportReport report = importer.run(List.of(order(2003, "wc-cancelled", false, "GONE-SKU")));
        assertThat(report.payments()).isZero();
        assertThat(orders.findByWooOrderId(2003L).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void refundedPaidGetsPayment() {
        OrderImportReport report = importer.run(List.of(order(2005, "wc-refunded", true, "GONE-SKU")));
        assertThat(report.payments()).isEqualTo(1);
        assertThat(orders.findByWooOrderId(2005L).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void unknownStatusIsReportedNotPersisted() {
        OrderImportReport report = importer.run(List.of(order(2004, "wc-bogus", false, "GONE-SKU")));
        assertThat(report.unknownStatuses()).contains("wc-bogus");
        assertThat(orders.findByWooOrderId(2004L)).isEmpty();
    }

    @Test
    void oneBadOrderFailsInIsolationOthersStillImport() {
        // The middle order has a malformed createdAt, which throws during its own
        // transaction. The two valid orders around it must still import and commit.
        SourceOrder bad = new SourceOrder(2102, "wc_order_2102", "wc-completed", "HUF",
                "not-a-real-date", null, 3768L, "X", "x@example.com", "+36", "1011", "Bp", "Fő 1.", null,
                "Foxpost", 0, 7400, 7400, false, null,
                List.of(new SourceOrderItem(55L, 56L, "GONE-SKU", "p", "1 kg", 2, 3700, 27, 7400)));

        OrderImportReport report = importer.run(List.of(
                order(2101, "wc-completed", true, "GONE-SKU"),
                bad,
                order(2103, "wc-completed", false, "GONE-SKU")));

        assertThat(report.imported()).isEqualTo(2);
        assertThat(report.failedCount()).isEqualTo(1);
        assertThat(report.failures()).containsKey(2102L);
        assertThat(orders.findByWooOrderId(2101L)).isPresent();
        assertThat(orders.findByWooOrderId(2103L)).isPresent();
        assertThat(orders.findByWooOrderId(2102L)).isEmpty(); // rolled back in isolation
    }
}
