package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.checkout.CheckoutService;
import hu.deposoft.webshop.application.payment.PaymentService;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Card payment entry and return landing (T10). The pay button POSTs here from
 * the confirmation page; the bank-return landing resolves payId -> order and
 * sends the customer back to the confirmation page (its banner reflects the
 * payment outcome).
 */
@Controller
@Slf4j
@RequiredArgsConstructor
public class PaymentController {

    private final CheckoutService checkoutService;
    private final PaymentService paymentService;
    private final PaymentRepository payments;

    @PostMapping("/penztar/fizetes/{clientKey}")
    public String startPayment(@PathVariable String clientKey) {
        Order order = checkoutService.findByClientKey(clientKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        // absolute URL of the starter's bank-return callback, derived from this request
        String returnUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/khpos/return").build().toUriString();
        try {
            PaymentService.StartResult result = paymentService.start(order, returnUrl);
            log.info("Payment start for order {}: returnUrl={} -> bank redirect={}",
                    clientKey, returnUrl, result.redirectUrl());
            return "redirect:" + result.redirectUrl();
        } catch (PaymentService.PaymentNotAllowedException e) {
            return "redirect:/penztar/koszonjuk/" + clientKey;
        }
    }

    /** Bank-return landing; payId appended by our KhposReturnUrlResolver. */
    @GetMapping("/penztar/fizetes/visszateres")
    public String paymentReturn(@RequestParam(required = false) String payId) {
        log.info("Payment return landing: payId={}", payId);
        if (payId != null) {
            var payment = payments.findByPayId(payId);
            if (payment.isPresent()) {
                String clientKey = payment.get().getOrder().getClientKey();
                log.info("Payment return matched payId={} -> order {} (clientKey={})",
                        payId, payment.get().getOrder().orderNumber(), clientKey);
                return "redirect:/penztar/koszonjuk/" + clientKey;
            }
            log.warn("Payment return with unknown payId={} -> falling back to home", payId);
        }
        return "redirect:/";
    }
}
