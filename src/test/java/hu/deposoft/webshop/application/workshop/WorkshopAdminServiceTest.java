package hu.deposoft.webshop.application.workshop;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.domain.catalog.Product;
import hu.deposoft.webshop.domain.catalog.ProductImage;
import hu.deposoft.webshop.domain.catalog.ProductImageRepository;
import hu.deposoft.webshop.domain.catalog.ProductRepository;
import hu.deposoft.webshop.domain.catalog.ProductStatus;
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

@SpringBootTest
@Testcontainers
@Transactional
class WorkshopAdminServiceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    @Autowired
    WorkshopService workshops;

    @Autowired
    CartService cart;

    @Autowired
    CheckoutService checkout;

    @Autowired
    ProductImageRepository images;

    @Autowired
    ProductRepository products;

    @Test
    void updatesWorkshopFields() {
        Product ws = workshops.createWorkshop("Régi név", "ws-upd-v1", "régi", 27);

        workshops.updateWorkshop(ws.getId(), "Új név", "ws-upd-v2", "új leírás", 5);

        Product reloaded = workshops.getWorkshop(ws.getId());
        assertThat(reloaded.getName()).isEqualTo("Új név");
        assertThat(reloaded.getSlug()).isEqualTo("ws-upd-v2");
        assertThat(reloaded.getVatRatePercent()).isEqualTo(5);
        assertThat(reloaded.getDescription()).isEqualTo("új leírás");
    }

    @Test
    void cancelsAnUnbookedSession() {
        Product ws = workshops.createWorkshop("WS", "ws-cancel", "x", 27);
        workshops.addSession(ws, NOW.plusDays(7), 5, 15_000L, "WS-CAN-1");

        Long sessionId = workshops.listSessions(ws.getId()).getFirst().getId();
        workshops.cancelSession(sessionId);

        assertThat(workshops.listSessions(ws.getId())).isEmpty();
    }

    @Test
    void refusesToCancelABookedSession() {
        Product ws = workshops.createWorkshop("WS", "ws-booked", "x", 27);
        workshops.addSession(ws, NOW.plusDays(7), 5, 15_000L, "WS-BK-1");
        String token = cart.addItem(null, "WS-BK-1", 1).token();
        checkout.placeOrder(token, new PlaceOrderCommand("ws-bk", "Teszt", "t@example.com", null,
                "1111", "Budapest", "Fő u. 1.", null, "pickup"));

        Long sessionId = workshops.listSessions(ws.getId()).getFirst().getId();
        assertThatThrownBy(() -> workshops.cancelSession(sessionId))
                .isInstanceOf(WorkshopService.SessionHasBookingsException.class);
    }

    @Test
    void listsWorkshops() {
        workshops.createWorkshop("A", "ws-a", "x", 27);
        workshops.createWorkshop("B", "ws-b", "x", 27);

        assertThat(workshops.listWorkshops()).extracting(Product::getName).contains("A", "B");
    }

    @Test
    void bookingsExposeAttendeeContactAndSeats() {
        Product ws = workshops.createWorkshop("Bútorfestés", "ws-book", "x", 27);
        workshops.addSession(ws, NOW.plusDays(7), 8, 15_000L, "WS-BOOK-1");
        String token = cart.addItem(null, "WS-BOOK-1", 2).token();
        checkout.placeOrder(token, new PlaceOrderCommand("ws-book-key", "Kovács Béla",
                "bela@example.com", "+36201234567", "1111", "Budapest", "Fő u. 1.", null, "pickup"));

        var bookings = workshops.bookings(ws.getId());

        assertThat(bookings).hasSize(1);
        var b = bookings.getFirst();
        assertThat(b.customerName()).isEqualTo("Kovács Béla");
        assertThat(b.email()).isEqualTo("bela@example.com");
        assertThat(b.phone()).isEqualTo("+36201234567");
        assertThat(b.seats()).isEqualTo(2);
        assertThat(b.sessionSku()).isEqualTo("WS-BOOK-1");
        assertThat(b.sessionStartAt()).isNotNull();
    }

    @Test
    void togglesPublicationStatus() {
        Product ws = workshops.createWorkshop("Státusz", "ws-status", "x", 27);
        assertThat(workshops.getWorkshop(ws.getId()).getStatus()).isEqualTo(ProductStatus.PUBLISHED);

        workshops.updateWorkshop(ws.getId(), "Státusz", "ws-status", "x", 27, ProductStatus.DRAFT);
        assertThat(workshops.getWorkshop(ws.getId()).getStatus()).isEqualTo(ProductStatus.DRAFT);

        workshops.updateWorkshop(ws.getId(), "Státusz", "ws-status", "x", 27, ProductStatus.PUBLISHED);
        assertThat(workshops.getWorkshop(ws.getId()).getStatus()).isEqualTo(ProductStatus.PUBLISHED);
    }

    @Test
    void updateWithoutStatusKeepsCurrent() {
        Product ws = workshops.createWorkshop("Marad", "ws-keep", "x", 27);
        workshops.updateWorkshop(ws.getId(), "Marad", "ws-keep", "x", 27, ProductStatus.DRAFT);

        // legacy overload (no status) must not flip it back
        workshops.updateWorkshop(ws.getId(), "Marad2", "ws-keep", "y", 27);

        assertThat(workshops.getWorkshop(ws.getId()).getStatus()).isEqualTo(ProductStatus.DRAFT);
    }

    @Test
    void deletesAnUnbookedWorkshopWithSessionsAndImages() {
        Product ws = workshops.createWorkshop("Törlendő", "ws-delete", "x", 27);
        workshops.addSession(ws, NOW.plusDays(7), 5, 15_000L, "WS-DEL-A");
        images.save(ProductImage.create(ws, "wp/photo.jpg", "alt", 0, true));
        Long id = ws.getId();

        workshops.deleteWorkshop(id);

        assertThat(products.findById(id)).isEmpty();
        assertThat(images.findByProductOrderByPositionAsc(ws)).isEmpty();
        assertThatThrownBy(() -> workshops.getWorkshop(id))
                .isInstanceOf(WorkshopService.NotFoundException.class);
    }

    @Test
    void refusesToDeleteAWorkshopWithBookings() {
        Product ws = workshops.createWorkshop("Foglalt", "ws-del-booked", "x", 27);
        workshops.addSession(ws, NOW.plusDays(7), 5, 15_000L, "WS-DEL-BK");
        String token = cart.addItem(null, "WS-DEL-BK", 1).token();
        checkout.placeOrder(token, new PlaceOrderCommand("ws-del-bk", "Teszt", "t@example.com", null,
                "1111", "Budapest", "Fő u. 1.", null, "pickup"));
        Long id = ws.getId();

        assertThatThrownBy(() -> workshops.deleteWorkshop(id))
                .isInstanceOf(WorkshopService.WorkshopHasBookingsException.class);

        assertThat(products.findById(id)).isPresent(); // nothing deleted
    }

    @Test
    void imagesExposesGalleryViewShape() {
        Product ws = workshops.createWorkshop("Galéria", "ws-gallery", "x", 27);
        images.save(ProductImage.create(ws, "wp/cover.jpg", "borító", 0, true));
        images.save(ProductImage.create(ws, "up/second.jpg", "második", 1, false));

        var gallery = workshops.images(ws);

        assertThat(gallery).hasSize(2);
        var cover = gallery.get(0);
        assertThat(cover.alt()).isEqualTo("borító");
        assertThat(cover.position()).isEqualTo(0);
        assertThat(cover.featured()).isTrue();
        assertThat(cover.url()).isNotBlank();
        assertThat(gallery.get(1).featured()).isFalse();
        assertThat(gallery.get(1).position()).isEqualTo(1);
    }

    @Test
    void sessionCountReflectsSessions() {
        Product ws = workshops.createWorkshop("Számláló", "ws-count", "x", 27);
        assertThat(workshops.sessionCount(ws.getId())).isZero();
        workshops.addSession(ws, NOW.plusDays(1), 5, 15_000L, "WS-CNT-1");
        workshops.addSession(ws, NOW.plusDays(2), 5, 15_000L, "WS-CNT-2");
        assertThat(workshops.sessionCount(ws.getId())).isEqualTo(2);
    }
}
