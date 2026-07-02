package hu.deposoft.webshop.api;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.cart.CartService.CartResult;
import hu.deposoft.webshop.application.cart.CartService.CartView;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Cart API (T8) — used by the webshop pages (htmx) and the static-site session
 * island alike. Thin: delegates to {@link CartService}; owns only the cookie and
 * HTTP-status mapping. All responses are no-store (session-dependent data).
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    public static final String CART_COOKIE = "nk_cart";
    private static final Duration CART_COOKIE_TTL = Duration.ofDays(30);
    private static final String NO_STORE = "no-store";

    private final CartService cartService;

    public record AddItemRequest(@NotBlank String sku, @Min(1) int quantity) {
    }

    public record ChangeQuantityRequest(@Min(0) int quantity) {
    }

    @GetMapping
    public CartView cart(@CookieValue(name = CART_COOKIE, required = false) String token,
                         HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
        return cartService.view(token);
    }

    @PostMapping("/items")
    public CartView addItem(@CookieValue(name = CART_COOKIE, required = false) String token,
                            @RequestBody @jakarta.validation.Valid AddItemRequest request,
                            HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
        CartResult result = cartService.addItem(token, request.sku(), request.quantity());
        if (!result.token().equals(token)) {
            response.addHeader(HttpHeaders.SET_COOKIE, cartCookie(result.token()).toString());
        }
        return result.view();
    }

    @PatchMapping("/items/{itemId}")
    public CartView changeQuantity(@CookieValue(name = CART_COOKIE, required = false) String token,
                                   @PathVariable long itemId,
                                   @RequestBody @jakarta.validation.Valid ChangeQuantityRequest request,
                                   HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
        return cartService.changeQuantity(token, itemId, request.quantity());
    }

    @DeleteMapping("/items/{itemId}")
    public CartView removeItem(@CookieValue(name = CART_COOKIE, required = false) String token,
                               @PathVariable long itemId,
                               HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
        return cartService.removeItem(token, itemId);
    }

    private ResponseCookie cartCookie(String token) {
        return ResponseCookie.from(CART_COOKIE, token)
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(CART_COOKIE_TTL)
                // Secure flag: terminated at the CDN/proxy in prod; localhost dev is http
                .build();
    }

    @ExceptionHandler(CartService.UnknownVariantException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String unknownVariant(CartService.UnknownVariantException e) {
        return e.getMessage();
    }

    @ExceptionHandler(CartService.NotOrderableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String notOrderable(CartService.NotOrderableException e) {
        return e.getMessage();
    }
}
