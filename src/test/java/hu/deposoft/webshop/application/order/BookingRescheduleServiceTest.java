package hu.deposoft.webshop.application.order;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.AvailabilityService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.checkout.OrderStatus;
import hu.deposoft.webshop.domain.order.AuditEntryRepository;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.workshop.WorkshopSession;
import hu.deposoft.webshop.domain.workshop.WorkshopSessionRepository;
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

@SpringBootTest
@Testcontainers
@Transactional
class BookingRescheduleServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired BookingRescheduleService reschedules;
    @Autowired OrderRepository orders;
    @Autowired AuditEntryRepository audit;
    @Autowired CartService cart;
    @Autowired CheckoutService checkout;
    @Autowired WorkshopService workshops;
    @Autowired WorkshopSessionRepository sessions;
    @Autowired AvailabilityService availability;
    @Autowired CatalogImporter importer;

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    private Product workshopWithTwoSessions(String key, long priceB, int capB) {
        Product ws = workshops.createWorkshop("WS " + key, "ws-" + key, "leiras", 27);
        workshops.addSession(ws, NOW.plusDays(7), 5, 15_000L, "WSA-" + key);
        workshops.addSession(ws, NOW.plusDays(8), capB, priceB, "WSB-" + key);
        return ws;
    }

    private Order bookSessionA(String key) {
        String token = cart.addItem(null, "WSA-" + key, 1).token();
        return checkout.placeOrder(token, new PlaceOrderCommand(key, "Teszt",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "gls"));
    }

    private Long sessionIdBySku(String sku) {
        return sessions.findAll().stream().filter(s -> sku.equals(s.getVariant().getSku()))
                .findFirst().orElseThrow().getId();
    }

    @Test
    void reschedulesToSamePriceSessionFreesSourceConsumesTarget() {
        workshopWithTwoSessions("ok", 15_000L, 5);
        Order order = bookSessionA("ok");
        OrderItem line = order.getItems().get(0);
        Variant sourceSeat = line.getVariant();
        Long targetSessionId = sessionIdBySku("WSB-ok");
        Variant targetSeat = sessions.findById(targetSessionId).orElseThrow().getVariant();

        int srcBefore = availability.availableQty(sourceSeat, AvailabilityService.NO_CART);
        int tgtBefore = availability.availableQty(targetSeat, AvailabilityService.NO_CART);

        reschedules.reschedule(line.getId(), targetSessionId);

        OrderItem moved = orders.findById(order.getId()).orElseThrow().getItems().get(0);
        assertThat(moved.getSku()).isEqualTo("WSB-ok");
        assertThat(moved.getVariant().getId()).isEqualTo(targetSeat.getId());
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(availability.availableQty(sourceSeat, AvailabilityService.NO_CART)).isEqualTo(srcBefore + 1);
        assertThat(availability.availableQty(targetSeat, AvailabilityService.NO_CART)).isEqualTo(tgtBefore - 1);
        assertThat(audit.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("order_item", String.valueOf(line.getId())))
                .anySatisfy(e -> assertThat(e.getAction()).isEqualTo("BOOKING_RESCHEDULED"));
    }

    @Test
    void rejectsDifferentPrice() {
        workshopWithTwoSessions("price", 18_000L, 5);
        Order order = bookSessionA("price");
        Long lineId = order.getItems().get(0).getId();
        Long targetSessionId = sessionIdBySku("WSB-price");
        assertThatThrownBy(() -> reschedules.reschedule(lineId, targetSessionId))
                .isInstanceOf(BookingRescheduleService.RescheduleNotAllowedException.class);
    }

    @Test
    void rejectsFullTarget() {
        workshopWithTwoSessions("full", 15_000L, 1);
        String fillTok = cart.addItem(null, "WSB-full", 1).token();
        checkout.placeOrder(fillTok, new PlaceOrderCommand("fill", "X", "x@e.com", null,
                "1111", "Budapest", "Fő u. 1.", null, "gls"));
        Order order = bookSessionA("full");
        Long lineId = order.getItems().get(0).getId();
        Long targetSessionId = sessionIdBySku("WSB-full");
        assertThatThrownBy(() -> reschedules.reschedule(lineId, targetSessionId))
                .isInstanceOf(BookingRescheduleService.RescheduleNotAllowedException.class);
    }

    @Test
    void rejectsTargetEqualsSource() {
        workshopWithTwoSessions("same", 15_000L, 5);
        Order order = bookSessionA("same");
        Long lineId = order.getItems().get(0).getId();
        Long sourceSessionId = sessionIdBySku("WSA-same");
        assertThatThrownBy(() -> reschedules.reschedule(lineId, sourceSessionId))
                .isInstanceOf(BookingRescheduleService.RescheduleNotAllowedException.class);
    }

    @Test
    void rejectsDifferentWorkshop() {
        workshopWithTwoSessions("wsone", 15_000L, 5);
        Product other = workshops.createWorkshop("Other", "ws-other", "l", 27);
        workshops.addSession(other, NOW.plusDays(9), 5, 15_000L, "OTHER-1");
        Order order = bookSessionA("wsone");
        Long lineId = order.getItems().get(0).getId();
        Long foreignSessionId = sessionIdBySku("OTHER-1");
        assertThatThrownBy(() -> reschedules.reschedule(lineId, foreignSessionId))
                .isInstanceOf(BookingRescheduleService.RescheduleNotAllowedException.class);
    }

    @Test
    void rejectsCancelledOrder() {
        workshopWithTwoSessions("canc", 15_000L, 5);
        Order order = bookSessionA("canc");
        order.transitionTo(OrderStatus.CANCELLED);
        orders.save(order);
        Long lineId = order.getItems().get(0).getId();
        Long targetSessionId = sessionIdBySku("WSB-canc");
        assertThatThrownBy(() -> reschedules.reschedule(lineId, targetSessionId))
                .isInstanceOf(BookingRescheduleService.RescheduleNotAllowedException.class);
    }

    @Test
    void notFoundForUnknownItemOrSession() {
        workshopWithTwoSessions("nf", 15_000L, 5);
        Order order = bookSessionA("nf");
        Long lineId = order.getItems().get(0).getId();
        Long targetSessionId = sessionIdBySku("WSB-nf");
        assertThatThrownBy(() -> reschedules.reschedule(999999L, targetSessionId))
                .isInstanceOf(hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException.class);
        assertThatThrownBy(() -> reschedules.reschedule(lineId, 999999L))
                .isInstanceOf(hu.deposoft.webshop.application.workshop.WorkshopService.NotFoundException.class);
    }

    @Test
    void rejectsNonWorkshopLine() {
        // seed a physical product
        hu.deposoft.webshop.integrations.woo.SourceProduct paint = new hu.deposoft.webshop.integrations.woo.SourceProduct(
                100L, "festek", "Festék", "simple", "publish", null, null, null, null, null,
                java.util.List.of(10L), java.util.List.of(), "FES-1", 3700L, null, null, null, true, 10, 250,
                java.util.List.of(), java.util.List.of(), java.util.List.of());
        importer.run(new hu.deposoft.webshop.integrations.woo.SourceCatalog(
                java.util.List.of(new hu.deposoft.webshop.integrations.woo.SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                java.util.List.<hu.deposoft.webshop.integrations.woo.SourceAttribute>of(),
                java.util.List.of(paint), java.util.List.of()));
        workshopWithTwoSessions("nonws", 15_000L, 5);

        String token = cart.addItem(null, "FES-1", 1).token();
        Order physical = checkout.placeOrder(token, new PlaceOrderCommand("nonws-ord", "T",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "gls"));
        Long physicalLineId = physical.getItems().get(0).getId();
        Long targetSessionId = sessionIdBySku("WSB-nonws");

        assertThatThrownBy(() -> reschedules.reschedule(physicalLineId, targetSessionId))
                .isInstanceOf(BookingRescheduleService.RescheduleNotAllowedException.class);
    }
}
