package hu.deposoft.webshop.web;

import hu.deposoft.webshop.api.CartController;
import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.checkout.CheckoutService.CheckoutView;
import hu.deposoft.webshop.application.checkout.PlaceOrderCommand;
import hu.deposoft.webshop.domain.order.Order;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Checkout pages on the WP-preserved slug (/penztar). Session-dependent by
 * design — strictly no-store. The hidden clientKey field (generated at form
 * render) makes the POST idempotent: back-button + resubmit lands on the same
 * order. Payment (KHPos) arrives in T10.
 */
@Controller
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final hu.deposoft.webshop.application.payment.PaymentService paymentService;
    private final hu.deposoft.webshop.domain.order.PaymentRepository payments;

    public record CheckoutForm(
            @NotBlank String clientKey,
            @NotBlank String customerName,
            @NotBlank @Email String email,
            String phone,
            @NotBlank String postcode,
            @NotBlank String city,
            @NotBlank String addressLine,
            String note,
            @NotBlank String shippingMethodCode) {
    }

    @GetMapping({"/penztar", "/penztar/"})
    public String checkout(@CookieValue(name = CartController.CART_COOKIE, required = false) String cartToken,
                           Model model, RedirectAttributes redirectAttributes, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        CheckoutView view;
        try {
            view = checkoutService.start(cartToken);
        } catch (CheckoutService.EmptyCartException e) {
            return "redirect:/kosar";
        } catch (CheckoutService.UnavailableItemsException e) {
            // Flash (not model) so the message survives the redirect to the cart,
            // where the customer can adjust the affected line. Name each offending
            // item AND state how many remain, and flash a sku -> remaining map so the
            // cart row badge shows the limit. Copy is Hungarian.
            String detail = e.items().stream()
                    .map(it -> it.available() > 0
                            ? it.name() + " (még " + it.available() + " elérhető)"
                            : it.name() + " (elfogyott)")
                    .reduce((a, b) -> a + ", " + b).orElse("");
            java.util.Map<String, Integer> remaining = new java.util.LinkedHashMap<>();
            e.items().forEach(it -> remaining.put(it.sku(), it.available()));
            redirectAttributes.addFlashAttribute("unavailableMessage",
                    "A kívánt mennyiségben már nem érhető el: " + detail
                            + ". Kérjük, csökkentsd a mennyiséget vagy vedd ki a kosárból.");
            redirectAttributes.addFlashAttribute("unavailableRemaining", remaining);
            return "redirect:/kosar";
        }
        model.addAttribute("checkout", view);
        model.addAttribute("clientKey", UUID.randomUUID().toString());
        return "checkout";
    }

    @PostMapping({"/penztar", "/penztar/"})
    public String placeOrder(@CookieValue(name = CartController.CART_COOKIE, required = false) String cartToken,
                             @ModelAttribute @jakarta.validation.Valid CheckoutForm form,
                             BindingResult bindingResult,
                             Model model, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (bindingResult.hasErrors()) {
            model.addAttribute("checkout", checkoutService.start(cartToken));
            model.addAttribute("clientKey", form.clientKey());
            return "checkout";
        }
        try {
            checkoutService.placeOrder(cartToken, new PlaceOrderCommand(
                    form.clientKey(), form.customerName(), form.email(), form.phone(),
                    form.postcode(), form.city(), form.addressLine(), form.note(),
                    form.shippingMethodCode()));
        } catch (CheckoutService.EmptyCartException e) {
            return "redirect:/kosar";
        } catch (CheckoutService.InsufficientStockException
                 | CheckoutService.UnavailableItemsException e) {
            model.addAttribute("stockError", true);
            model.addAttribute("checkout", checkoutService.start(cartToken));
            model.addAttribute("clientKey", form.clientKey());
            return "checkout";
        }
        return "redirect:/penztar/koszonjuk/" + form.clientKey();
    }

    @GetMapping("/penztar/koszonjuk/{clientKey}")
    public String confirmation(@PathVariable String clientKey, Model model, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        Order order = checkoutService.findByClientKey(clientKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("order", order);
        model.addAttribute("vatLines", checkoutService.vatBreakdown(order));
        model.addAttribute("paymentEnabled", paymentService.isPaymentEnabled());
        model.addAttribute("lastPayment",
                payments.findFirstByOrderOrderByIdDesc(order).orElse(null));
        return "order-confirmation";
    }
}
