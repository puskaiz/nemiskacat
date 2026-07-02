package hu.deposoft.webshop.web;

import hu.deposoft.webshop.api.CartController;
import hu.deposoft.webshop.application.cart.CartService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Cart page on the WP-preserved URL (/kosar). Session-dependent by design — the
 * allowed exception to the cacheable-HTML rule (CLAUDE.md #2) — therefore
 * strictly no-store.
 */
@Controller
@RequiredArgsConstructor
public class CartPageController {

    private final CartService cartService;

    @GetMapping({"/kosar", "/kosar/"})
    public String cart(@CookieValue(name = CartController.CART_COOKIE, required = false) String token,
                       Model model, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        model.addAttribute("cart", cartService.view(token));
        return "cart";
    }
}
