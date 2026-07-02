package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.application.payment.PaymentService;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.integrations.woo.SourceAttribute;
import hu.deposoft.webshop.integrations.woo.SourceCatalog;
import hu.deposoft.webshop.integrations.woo.SourceCategory;
import hu.deposoft.webshop.integrations.woo.SourceProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** T10 web flow: pay button -> bank redirect -> return landing -> confirmation banner. */
@SpringBootTest
@Testcontainers
@Transactional
class PaymentPagesTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    WebApplicationContext context;

    @Autowired
    CatalogImporter importer;

    @Autowired
    CartService cartService;

    @Autowired
    CheckoutService checkoutService;

    @Autowired
    PaymentService paymentService;

    @MockitoBean
    PaymentGateway gateway;

    MockMvc mvc;
    Order order;

    @BeforeEach
    void setUp() {
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
        SourceProduct product = new SourceProduct(100L, "festek", "Fizetés Teszt Festék", "simple", "publish",
                null, null, null, null, null, List.of(10L), List.of(),
                "FES-1", 3700L, null, null, null, true, 10, 250, List.of(), List.of(), List.of());
        importer.run(new SourceCatalog(
                List.of(new SourceCategory(10L, "kat", "Kat", null, 0, null, null, null)),
                List.<SourceAttribute>of(), List.of(product), List.of()));
        String token = cartService.addItem(null, "FES-1", 1).token();
        order = checkoutService.placeOrder(token, new PlaceOrderCommand(
                "pay-page-key", "Teszt Elek", "t@example.com", null,
                "1111", "Budapest", "Fő u. 1.", null, "pickup"));
        when(gateway.isEnabled()).thenReturn(true);
        when(gateway.initPayment(any(), anyLong(), any())).thenReturn(
                new PaymentGateway.InitResult("PAY-W1", "https://bank.example/pay/PAY-W1"));
    }

    @Test
    void confirmationShowsPayButtonForNewOrder() throws Exception {
        mvc.perform(get("/penztar/koszonjuk/pay-page-key"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Fizetés bankkártyával")));
    }

    @Test
    void startPaymentRedirectsToBank() throws Exception {
        mvc.perform(post("/penztar/fizetes/pay-page-key").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://bank.example/pay/PAY-W1"));
    }

    @Test
    void returnLandingRedirectsToConfirmation() throws Exception {
        paymentService.start(order, "http://localhost/khpos/return");

        mvc.perform(get("/penztar/fizetes/visszateres").param("payId", "PAY-W1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/penztar/koszonjuk/pay-page-key"));
    }

    @Test
    void confirmationShowsPaidBannerAfterConfirmedResult() throws Exception {
        paymentService.start(order, "http://localhost/khpos/return");
        paymentService.applyGatewayResult("PAY-W1", PaymentGateway.ResultKind.CONFIRMED, "OK");

        mvc.perform(get("/penztar/koszonjuk/pay-page-key"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Fizetés sikeres")));
    }

    @Test
    void confirmationShowsRetryAfterRejectedPayment() throws Exception {
        paymentService.start(order, "http://localhost/khpos/return");
        paymentService.applyGatewayResult("PAY-W1", PaymentGateway.ResultKind.REJECTED, "declined");

        mvc.perform(get("/penztar/koszonjuk/pay-page-key"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A fizetés nem sikerült")))
                .andExpect(content().string(containsString("Fizetés bankkártyával")));
    }
}
