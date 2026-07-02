package hu.deposoft.webshop.application.cart;

import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.cart.CartService.CartView;
import hu.deposoft.webshop.domain.cart.CartRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T8 core: server-side guest cart — token creation, increment on same variant,
 * soft stock check on add, current (sale) prices in totals.
 */
@SpringBootTest
@Testcontainers
@Transactional
class CartServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    CartService cartService;

    @Autowired
    CatalogImporter importer;

    @Autowired
    CartRepository carts;

    @BeforeEach
    void seedCatalog() {
        SourceProduct inStock = new SourceProduct(100L, "festek", "Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, null, List.of(), List.of(), List.of());
        SourceProduct onSale = new SourceProduct(101L, "akcios-festek", "Akciós Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "AKC-1", 5000L, 3900L, null, null, true, 10, null, List.of(), List.of(), List.of());
        SourceProduct soldOut = new SourceProduct(102L, "elfogyott", "Elfogyott", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "OOS-1", 5000L, null, null, null, true, 0, null, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(),
                List.of(inStock, onSale, soldOut), List.of()));
    }

    @Test
    void addToNewCartCreatesCartWithToken() {
        CartService.CartResult result = cartService.addItem(null, "FES-1", 2);

        assertThat(result.token()).isNotBlank();
        CartView view = result.view();
        assertThat(view.count()).isEqualTo(2);
        assertThat(view.items()).hasSize(1);
        assertThat(view.items().getFirst().name()).isEqualTo("Festék");
        assertThat(view.items().getFirst().lineTotalFormatted()).isEqualTo("7 400 Ft");
        assertThat(carts.findByToken(result.token())).isPresent();
    }

    @Test
    void addingSameVariantIncrementsQuantity() {
        String token = cartService.addItem(null, "FES-1", 1).token();
        CartView view = cartService.addItem(token, "FES-1", 2).view();

        assertThat(view.items()).hasSize(1);
        assertThat(view.count()).isEqualTo(3);
    }

    @Test
    void addUsesSalePriceInTotals() {
        CartView view = cartService.addItem(null, "AKC-1", 1).view();

        assertThat(view.totalFormatted()).isEqualTo("3 900 Ft");
    }

    @Test
    void addRejectsNonOrderableVariant() {
        assertThatThrownBy(() -> cartService.addItem(null, "OOS-1", 1))
                .isInstanceOf(CartService.NotOrderableException.class);
    }

    @Test
    void addRejectsUnknownSku() {
        assertThatThrownBy(() -> cartService.addItem(null, "NO-SUCH", 1))
                .isInstanceOf(CartService.UnknownVariantException.class);
    }

    @Test
    void quantityUpdateAndRemovalWork() {
        CartService.CartResult added = cartService.addItem(null, "FES-1", 1);
        long itemId = added.view().items().getFirst().id();

        CartView updated = cartService.changeQuantity(added.token(), itemId, 5);
        assertThat(updated.count()).isEqualTo(5);

        CartView removed = cartService.removeItem(added.token(), itemId);
        assertThat(removed.items()).isEmpty();
        assertThat(removed.count()).isZero();
    }

    @Test
    void viewForUnknownTokenIsEmpty() {
        CartView view = cartService.view("does-not-exist");

        assertThat(view.items()).isEmpty();
        assertThat(view.count()).isZero();
    }
}
