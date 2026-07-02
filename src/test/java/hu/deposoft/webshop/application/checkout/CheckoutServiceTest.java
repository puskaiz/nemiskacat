package hu.deposoft.webshop.application.checkout;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.AvailabilityService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.catalog.VariantRepository;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Reservation;
import hu.deposoft.webshop.domain.order.ReservationRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T9 acceptance: fee+VAT totals on a real order, double-submit protection
 * (client key), stock re-check at placement, reservation hold + expiry, and the
 * live availability ledger (placement reduces availability without a sync).
 */
@SpringBootTest
@Testcontainers
@Transactional
class CheckoutServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    CheckoutService checkoutService;

    @Autowired
    CartService cartService;

    @Autowired
    CatalogImporter importer;

    @Autowired
    OrderRepository orders;

    @Autowired
    ReservationRepository reservations;

    @Autowired
    VariantRepository variants;

    @Autowired
    ProductRepository products;

    @Autowired
    AvailabilityService availability;

    @BeforeEach
    void seedCatalog() {
        // 250 g, 3 700 Ft, 27% VAT, 10 in stock
        SourceProduct paint = product(100L, "festek", "Festék", "FES-1", 3700L, 250, 10, null);
        // 300 g, 8 200 Ft, 5% VAT (book), 5 in stock
        SourceProduct book = product(101L, "konyv", "Könyv", "KON-1", 8200L, 300, 5, "reduced-rate");
        // heavy: 6 kg
        SourceProduct heavy = product(102L, "nehez", "Nehéz", "NEH-1", 12000L, 6000, 10, null);
        // single piece for reservation tests
        SourceProduct unique = product(103L, "egyedi", "Egyedi darab", "EGY-1", 25000L, 1000, 1, null);
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(),
                List.of(paint, book, heavy, unique), List.of()));
        // switch on the checkout hold for the unique product
        products.findByExternalId(103L).orElseThrow().setReserveOnCheckout(true);
    }

    private SourceProduct product(long wooId, String slug, String name, String sku, long price,
                                  int weightGrams, int stock, String taxClass) {
        return new SourceProduct(wooId, slug, name, "simple", "publish",
                null, null, taxClass, null, null, List.of(10L), List.of(),
                sku, price, null, null, null, true, stock, weightGrams, List.of(), List.of(), List.of());
    }

    private PlaceOrderCommand command(String clientKey, String shippingCode) {
        return new PlaceOrderCommand(clientKey, "Teszt Elek", "teszt@example.com", "+36201234567",
                "1111", "Budapest", "Fő utca 1.", null, shippingCode);
    }

    // ---- fees + VAT on a placed order ----

    @Test
    void placedOrderCarriesShippingFeeAndVatBreakdown() {
        // 2 × 3 700 (27%) + 1 × 8 200 (5%) = 15 600; weight 800 g -> GLS 850
        String token = cartService.addItem(null, "FES-1", 2).token();
        cartService.addItem(token, "KON-1", 1);

        Order order = checkoutService.placeOrder(token, command("key-1", "gls"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(order.getItemsGrossHuf()).isEqualTo(15_600);
        assertThat(order.getShipGrossHuf()).isEqualTo(850);
        assertThat(order.getTotalGrossHuf()).isEqualTo(16_450);

        // VAT: 27% gross = 7 400 (items) + 850 (shipping) = 8 250 -> net 6 496, vat 1 754
        //       5% gross = 8 200 -> net 7 810, vat 390
        var vat = checkoutService.vatBreakdown(order);
        assertThat(vat).extracting(v -> v.ratePercent()).containsExactly(5, 27);
        assertThat(vat.get(0).vatHuf()).isEqualTo(390);
        assertThat(vat.get(1).grossHuf()).isEqualTo(8_250);
    }

    @Test
    void weightBandsAndFreeShippingApplyToTheCart() {
        // 6 kg -> third band 1 700; cart 12 000 under threshold
        String token = cartService.addItem(null, "NEH-1", 1).token();
        var checkout = checkoutService.start(token);
        assertThat(checkout.shippingOptions()).anySatisfy(o -> {
            if (o.code().equals("gls")) {
                assertThat(o.grossHuf()).isEqualTo(1_700);
            }
        });

        // 4 × 12 000 = 48 000 >= 39 000 -> free GLS
        cartService.addItem(token, "NEH-1", 3);
        var checkout2 = checkoutService.start(token);
        assertThat(checkout2.shippingOptions()).anySatisfy(o -> {
            if (o.code().equals("gls")) {
                assertThat(o.grossHuf()).isZero();
            }
        });
    }

    // ---- idempotency (double submit) ----

    @Test
    void sameClientKeyYieldsTheSameOrderWithoutDuplicates() {
        String token = cartService.addItem(null, "FES-1", 1).token();

        Order first = checkoutService.placeOrder(token, command("dup-key", "pickup"));
        Order second = checkoutService.placeOrder(token, command("dup-key", "pickup"));

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(orders.count()).isEqualTo(1);
    }

    // ---- stock checks + ledger ----

    @Test
    void placementFailsWhenStockIsInsufficientAndWritesNoOrder() {
        String token = cartService.addItem(null, "KON-1", 5).token();
        // someone else buys 3 of the 5 in the meantime
        String other = cartService.addItem(null, "KON-1", 3).token();
        checkoutService.placeOrder(other, command("other-key", "pickup"));

        assertThatThrownBy(() -> checkoutService.placeOrder(token, command("key-2", "pickup")))
                .isInstanceOf(CheckoutService.InsufficientStockException.class);
        assertThat(orders.findByClientKey("key-2")).isEmpty();
    }

    @Test
    void placementReducesAvailabilityWithoutAnySync() {
        Variant variant = variants.findBySku("FES-1").orElseThrow();
        int before = availability.availableQty(variant, "");

        String token = cartService.addItem(null, "FES-1", 4).token();
        checkoutService.placeOrder(token, command("ledger-key", "pickup"));

        assertThat(availability.availableQty(variant, "")).isEqualTo(before - 4);
    }

    // ---- reservation (15-minute hold) + expiry ----

    @Test
    void checkoutStartHoldsFlaggedItemsAgainstOtherCarts() {
        String holder = cartService.addItem(null, "EGY-1", 1).token();
        checkoutService.start(holder); // creates the 15-min hold

        Variant variant = variants.findBySku("EGY-1").orElseThrow();
        // the holder still sees its piece, others do not
        assertThat(availability.availableQty(variant, holder)).isEqualTo(1);
        assertThat(availability.availableQty(variant, "someone-else")).isZero();
        assertThatThrownBy(() -> cartService.addItem(null, "EGY-1", 1))
                .isInstanceOf(CartService.NotOrderableException.class);
    }

    @Test
    void expiredReservationNoLongerBlocks() {
        Variant variant = variants.findBySku("EGY-1").orElseThrow();
        reservations.save(Reservation.hold(variant, "old-cart", 1,
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1)));

        assertThat(availability.availableQty(variant, "someone-else")).isEqualTo(1);
        // and the piece can actually be bought
        String token = cartService.addItem(null, "EGY-1", 1).token();
        Order order = checkoutService.placeOrder(token, command("exp-key", "pickup"));
        assertThat(order.getId()).isNotNull();
    }

    @Test
    void placementClearsOwnReservationAndCart() {
        String token = cartService.addItem(null, "EGY-1", 1).token();
        checkoutService.start(token);
        assertThat(reservations.count()).isEqualTo(1);

        checkoutService.placeOrder(token, command("clear-key", "pickup"));

        assertThat(reservations.count()).isZero();
        assertThat(cartService.view(token).items()).isEmpty();
    }

    // ---- T24 phase 1: mixed cart foundations ----

    @Test
    void workshopLineSnapshotsBillingoSourceWithConfigurableVatAndIsExcludedFromShipping() {
        // a workshop product: invoiced by Billingo, an event (no shipping), VAT 27%
        var ws = products.save(hu.deposoft.webshop.domain.catalog.Product.create(
                900L, "butorfestes-workshop", "Bútorfestés workshop",
                hu.deposoft.webshop.domain.catalog.ProductType.SIMPLE,
                hu.deposoft.webshop.domain.catalog.ProductStatus.PUBLISHED));
        ws.setInvoiceSource(hu.deposoft.webshop.domain.catalog.InvoiceSource.BILLINGO);
        ws.setFulfilmentType(hu.deposoft.webshop.domain.catalog.FulfilmentType.EVENT);
        ws.setVatRatePercent(27);
        var seat = hu.deposoft.webshop.domain.catalog.Variant.create(ws, null, true);
        seat.setSku("WS-1");
        seat.setRegularPriceHuf(15_000L);
        seat.setManageStock(false); // capacity handling comes in phase 2; unmanaged = orderable
        ws.addVariant(seat);
        products.save(ws);

        // mixed cart: 1 paint (FES-1, 250 g, KULCS_SOFT) + 1 workshop seat (EVENT, BILLINGO)
        String token = cartService.addItem(null, "FES-1", 1).token();
        cartService.addItem(token, "WS-1", 1);
        Order order = checkoutService.placeOrder(token, command("ws-key", "gls"));

        var paintLine = order.getItems().stream().filter(i -> "FES-1".equals(i.getSku())).findFirst().orElseThrow();
        var workshopLine = order.getItems().stream().filter(i -> "WS-1".equals(i.getSku())).findFirst().orElseThrow();
        assertThat(paintLine.getInvoiceSource()).isEqualTo(hu.deposoft.webshop.domain.catalog.InvoiceSource.KULCS_SOFT);
        assertThat(workshopLine.getInvoiceSource()).isEqualTo(hu.deposoft.webshop.domain.catalog.InvoiceSource.BILLINGO);
        assertThat(workshopLine.getTaxRatePercent()).isEqualTo(27);
        // shipping reflects only the paint's weight (250 g → GLS 850); the workshop adds none
        assertThat(order.getShipGrossHuf()).isEqualTo(850);
    }
}
