package hu.deposoft.webshop.domain.order;

import hu.deposoft.webshop.domain.checkout.OrderStatus;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class OrderImportedTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    OrderRepository orders;

    @Test
    void importedOrderKeepsFinalStatusDateAndProvenance() {
        OffsetDateTime placed = OffsetDateTime.of(2015, 3, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        Order o = Order.imported(1024L, OrderStatus.COMPLETED, "Nagy Ágnes", "agi@example.com",
                "+36301234567", "1011", "Budapest", "Fő utca 1.", null,
                "Foxpost", 990, 7400, 8390, placed);

        Order saved = orders.save(o);

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(saved.getClientKey()).isEqualTo("woo-1024");
        assertThat(saved.getWooOrderId()).isEqualTo(1024L);
        assertThat(saved.getSource()).isEqualTo("WOO_IMPORT");
        assertThat(saved.getCreatedAt()).isEqualTo(placed); // NOT now()
        assertThat(saved.getTotalGrossHuf()).isEqualTo(8390);
        assertThat(saved.getItemsGrossHuf()).isEqualTo(7400);
        assertThat(saved.getShipGrossHuf()).isEqualTo(990);
        assertThat(orders.findByWooOrderId(1024L)).isPresent();
    }

    @Test
    void blankRequiredSnapshotFieldsBecomePlaceholders() {
        Order o = Order.imported(7L, OrderStatus.CANCELLED, "  ", "buyer@example.com",
                null, "", "", "", null, null, 0, 0, 0,
                OffsetDateTime.now(ZoneOffset.UTC));
        Order saved = orders.save(o);
        assertThat(saved.getCustomerName()).isEqualTo("—");
        assertThat(saved.getCity()).isEqualTo("—");
        assertThat(saved.getShipMethodName()).isEqualTo("—");
        assertThat(saved.getPostcode()).isEqualTo("—");
        assertThat(saved.getAddressLine()).isEqualTo("—");
    }
}
