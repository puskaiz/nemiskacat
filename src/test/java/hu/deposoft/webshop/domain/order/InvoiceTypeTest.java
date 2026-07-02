package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/** An invoice and a credit note can coexist for the same (order, source). */
@SpringBootTest
@Testcontainers
@Transactional
class InvoiceTypeTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    InvoiceRepository invoices;

    @Autowired
    OrderRepository orders;

    @Test
    void invoiceAndCreditNoteCoexistPerSource() {
        // a bare order row is enough for the FK; use the orders repo to persist one via JPA
        Order order = orders.save(Order.place("itype-key", "T", "t@example.com", null,
                "1111", "B", "Fő u. 1.", null, "pickup", "Pickup", 0));

        invoices.save(Invoice.of(order, InvoiceSource.BILLINGO));
        invoices.save(Invoice.creditNote(order, InvoiceSource.BILLINGO));

        assertThat(invoices.findByOrderAndSourceAndType(order, InvoiceSource.BILLINGO, InvoiceType.INVOICE)).isPresent();
        assertThat(invoices.findByOrderAndSourceAndType(order, InvoiceSource.BILLINGO, InvoiceType.CREDIT_NOTE)).isPresent();
    }
}
