package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogQueryService;
import hu.deposoft.webshop.application.catalog.CatalogQueryService.ProductPageView;
import hu.deposoft.webshop.application.catalog.CatalogQueryService.WorkshopListItemView;
import hu.deposoft.webshop.application.catalog.CatalogQueryService.WorkshopSessionView;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.workshop.WorkshopService;
import hu.deposoft.webshop.domain.catalog.Money;
import hu.deposoft.webshop.domain.catalog.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * T24 phase 4 (frontend): the workshop event page renders its sessions as a
 * date-row picker with a seats badge (free seats / last few / sold out), and the
 * /workshopok listing shows each workshop with its soonest date and from-price.
 */
@SpringBootTest
@Testcontainers
@Transactional
class WorkshopPagesTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);
    private static final String PRICE = Money.huf(15_000L).formatted(); // NBSP thousands separator

    @Autowired
    WebApplicationContext context;

    @Autowired
    WorkshopService workshops;

    @Autowired
    CartService cart;

    @Autowired
    CheckoutService checkout;

    @Autowired
    CatalogQueryService catalog;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).build();
        Product ws = workshops.createWorkshop("Bútorfestés workshop", "butorfestes-workshop",
                "<p>Egész napos élmény.</p>", 27);
        workshops.addSession(ws, NOW.plusDays(7), 8, 15_000L, "WS-1");   // plenty free
        workshops.addSession(ws, NOW.plusDays(14), 3, 15_000L, "WS-2");  // last few (<= threshold)
        workshops.addSession(ws, NOW.plusDays(21), 1, 15_000L, "WS-3");  // will be sold out
        // book the only seat of the third session
        String token = cart.addItem(null, "WS-3", 1).token();
        checkout.placeOrder(token, new PlaceOrderCommand("ws-sellout", "Teszt Elek",
                "t@example.com", null, "1111", "Budapest", "Fő u. 1.", null, "pickup"));
    }

    // ---- read model ----

    @Test
    void workshopPageExposesSortedSessionsWithSeatBadges() {
        ProductPageView view = catalog.productPage("butorfestes-workshop").orElseThrow();

        assertThat(view.workshop()).isTrue();
        List<WorkshopSessionView> sessions = view.sessions();
        assertThat(sessions).extracting(WorkshopSessionView::sku).containsExactly("WS-1", "WS-2", "WS-3");

        assertThat(sessions.get(0).seatsText()).isEqualTo("8 szabad hely");
        assertThat(sessions.get(0).orderable()).isTrue();
        assertThat(sessions.get(0).soldOut()).isFalse();

        assertThat(sessions.get(1).seatsText()).isEqualTo("Utolsó pár hely!");
        assertThat(sessions.get(1).low()).isTrue();

        assertThat(sessions.get(2).seatsText()).isEqualTo("Betelt");
        assertThat(sessions.get(2).soldOut()).isTrue();
        assertThat(sessions.get(2).orderable()).isFalse();
    }

    @Test
    void workshopListShowsSoonestDateAndFromPrice() {
        List<WorkshopListItemView> items = catalog.workshopList();

        assertThat(items).hasSize(1);
        WorkshopListItemView item = items.getFirst();
        assertThat(item.name()).isEqualTo("Bútorfestés workshop");
        assertThat(item.url()).isEqualTo("/product/butorfestes-workshop/");
        assertThat(item.priceFromFormatted()).isEqualTo(PRICE);
        assertThat(item.hasUpcoming()).isTrue();
        assertThat(item.nextDateLabel()).isNotBlank();
    }

    // ---- rendered pages ----

    @Test
    void eventPageRendersTheSessionPicker() throws Exception {
        mvc.perform(get("/product/butorfestes-workshop/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-sku=\"WS-1\"")))
                .andExpect(content().string(containsString("8 szabad hely")))
                .andExpect(content().string(containsString("Utolsó pár hely!")))
                .andExpect(content().string(containsString("Betelt")))
                .andExpect(content().string(containsString("Jelentkezem")));
    }

    @Test
    void listingPageRendersWorkshops() throws Exception {
        mvc.perform(get("/workshopok"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bútorfestés workshop")))
                .andExpect(content().string(containsString(PRICE)));
    }
}
