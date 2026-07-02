package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class OrderAdminQueryServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    OrderAdminQueryService query;

    @Autowired
    CartService cart;

    @Autowired
    CheckoutService checkout;

    @Autowired
    CatalogImporter importer;

    @BeforeEach
    void seed() {
        SourceProduct paint = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(paint), List.of()));
        String token = cart.addItem(null, "FES-1", 1).token();
        checkout.placeOrder(token, new PlaceOrderCommand("o-1", "Kovács Béla", "bela@example.com", null,
                "1111", "Budapest", "Fő u. 1.", null, "pickup"));
    }

    @Test
    void listsOrdersWithTotal() {
        var page = query.list(null, null, null, null, 0, 20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().customerName()).isEqualTo("Kovács Béla");
        assertThat(page.items().getFirst().status()).isEqualTo(OrderStatus.NEW);
    }

    @Test
    void filtersBySearchTerm() {
        assertThat(query.list(null, null, null, "kovács", 0, 20).total()).isEqualTo(1);
        assertThat(query.list(null, null, null, "nincs-ilyen", 0, 20).total()).isZero();
    }

    @Test
    void detailExposesLines() {
        Long id = query.list(null, null, null, null, 0, 20).items().getFirst().id();

        var detail = query.detail(id);

        assertThat(detail.customerName()).isEqualTo("Kovács Béla");
        assertThat(detail.lines()).extracting(OrderAdminQueryService.LineView::sku).contains("FES-1");
    }

    @Test
    void filtersByStatus() {
        assertThat(query.list(OrderStatus.NEW, null, null, null, 0, 20).total()).isEqualTo(1);
        assertThat(query.list(OrderStatus.PAID, null, null, null, 0, 20).total()).isZero();
    }
}
