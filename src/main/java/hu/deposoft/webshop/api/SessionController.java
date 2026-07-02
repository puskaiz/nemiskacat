package hu.deposoft.webshop.api;

import hu.deposoft.webshop.application.cart.CartService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The session island endpoint (TERV §3.4): cacheable pages never contain
 * session data — the shared client-side JS asks here for the cart count (and
 * later the logged-in state, T13). Always no-store. Without a cart cookie the
 * response is a constant — no database is touched (the island may skip the call
 * entirely).
 *
 * Accessing the deferred {@link CsrfToken} here makes Security write the
 * XSRF-TOKEN cookie, so JS clients pick up their CSRF token on this call.
 */
@RestController
@RequiredArgsConstructor
public class SessionController {

    private final CartService cartService;

    public record SessionView(int cartCount, boolean authenticated) {
    }

    @GetMapping("/api/session")
    public SessionView session(@CookieValue(name = CartController.CART_COOKIE, required = false) String cartToken,
                               CsrfToken csrfToken,
                               HttpServletResponse response) {
        csrfToken.getToken(); // force cookie write for JS clients
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        int count = cartToken == null ? 0 : cartService.itemCount(cartToken);
        return new SessionView(count, false);
    }
}
