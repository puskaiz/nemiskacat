package hu.deposoft.webshop.application.workshop;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.AvailabilityService;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.domain.catalog.FulfilmentType;
import hu.deposoft.webshop.domain.catalog.InvoiceSource;
import hu.deposoft.webshop.domain.catalog.ProductType;
import hu.deposoft.webshop.domain.catalog.Variant;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T24 phase 2: workshops as catalog variants with capacity-based availability.
 * A session's seats = capacity − non-cancelled bookings − foreign holds; past
 * sessions are not bookable.
 */
@SpringBootTest
@Testcontainers
@Transactional
class WorkshopAvailabilityTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    @Autowired
    WorkshopService workshops;

    @Autowired
    AvailabilityService availability;

    @Autowired
    CartService cart;

    @Autowired
    CheckoutService checkout;

    private Variant futureSession(String sku, int capacity) {
        var workshop = workshops.createWorkshop("Bútorfestés workshop", "butorfestes-workshop-" + sku,
                "Egész napos workshop", 27);
        return workshops.addSession(workshop, NOW.plusDays(7), capacity, 15_000L, sku);
    }

    private PlaceOrderCommand order(String key) {
        return new PlaceOrderCommand(key, "Teszt Elek", "t@example.com", null,
                "1111", "Budapest", "Fő u. 1.", null, "pickup");
    }

    @Test
    void workshopIsCreatedAsBillingoEventProduct() {
        Variant seat = futureSession("WS-A", 3);

        assertThat(seat.getProduct().getType()).isEqualTo(ProductType.WORKSHOP);
        assertThat(seat.getProduct().getInvoiceSource()).isEqualTo(InvoiceSource.BILLINGO);
        assertThat(seat.getProduct().getFulfilmentType()).isEqualTo(FulfilmentType.EVENT);
        assertThat(seat.getProduct().getVatRatePercent()).isEqualTo(27);
    }

    @Test
    void capacityIsTheAvailabilityBaseline() {
        Variant seat = futureSession("WS-B", 3);

        assertThat(availability.availableQty(seat, "")).isEqualTo(3);
        assertThat(availability.isOrderable(seat, "")).isTrue();
    }

    @Test
    void bookingReducesAvailableSeats() {
        Variant seat = futureSession("WS-C", 3);

        String token = cart.addItem(null, "WS-C", 2).token();
        checkout.placeOrder(token, order("ws-c-key"));

        assertThat(availability.availableQty(seat, "")).isEqualTo(1);
    }

    @Test
    void soldOutWhenCapacityReached() {
        Variant seat = futureSession("WS-D", 1);

        String token = cart.addItem(null, "WS-D", 1).token();
        checkout.placeOrder(token, order("ws-d-key"));

        assertThat(availability.isOrderable(seat, "")).isFalse();
        // a further attempt cannot be fulfilled
        assertThat(availability.canFulfil(seat, "other", 1)).isFalse();
    }

    @Test
    void cannotBookMoreSeatsThanCapacityAtCheckout() {
        futureSession("WS-E", 2);

        String token = cart.addItem(null, "WS-E", 2).token();
        // someone else grabs both seats first
        String other = cart.addItem(null, "WS-E", 2).token();
        checkout.placeOrder(other, order("ws-e-other"));

        assertThatThrownBy(() -> checkout.placeOrder(token, order("ws-e-key")))
                .isInstanceOf(CheckoutService.InsufficientStockException.class);
    }

    @Test
    void pastSessionIsNotBookable() {
        var workshop = workshops.createWorkshop("Múltbeli workshop", "multbeli-workshop", "x", 27);
        Variant seat = workshops.addSession(workshop, NOW.minusDays(1), 5, 15_000L, "WS-PAST");

        assertThat(availability.isOrderable(seat, "")).isFalse();
    }
}
