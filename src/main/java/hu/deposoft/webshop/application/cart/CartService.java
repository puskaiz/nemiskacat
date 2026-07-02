package hu.deposoft.webshop.application.cart;

import hu.deposoft.webshop.domain.cart.Cart;
import hu.deposoft.webshop.domain.cart.CartItem;
import hu.deposoft.webshop.domain.cart.CartRepository;
import hu.deposoft.webshop.domain.catalog.EffectivePrice;
import hu.deposoft.webshop.domain.catalog.PriceCalculator;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.catalog.VariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Server-side guest cart (T8). The caller holds only the opaque token (cookie);
 * all business rules live here: soft stock check on add (overselling protection
 * point 1 of 3, TERV §3.7), quantity invariants, current-price totals. Prices are
 * never stored on items — checkout locks them (T9).
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CartRepository carts;
    private final VariantRepository variants;
    private final hu.deposoft.webshop.application.catalog.AvailabilityService availabilityService;
    private final hu.deposoft.webshop.domain.catalog.ProductImageRepository productImages;
    private final hu.deposoft.webshop.config.WebshopProperties properties;

    private final PriceCalculator priceCalculator = new PriceCalculator();

    // ---- views / results ----

    public record CartItemView(long id, String name, String variantLabel, String sku,
                               int quantity, String unitPriceFormatted, String lineTotalFormatted,
                               String productUrl, String imageUrl) {
    }

    public record CartView(List<CartItemView> items, int count, String totalFormatted) {

        public static CartView empty() {
            return new CartView(List.of(), 0, null);
        }
    }

    /** The view plus the token the caller must persist in the cookie. */
    public record CartResult(String token, CartView view) {
    }

    public static class UnknownVariantException extends RuntimeException {
        public UnknownVariantException(String key) {
            super("No variant for '%s'".formatted(key));
        }
    }

    public static class NotOrderableException extends RuntimeException {
        public NotOrderableException(String sku) {
            super("Variant '%s' is not orderable".formatted(sku));
        }
    }

    // ---- operations ----

    @Transactional(readOnly = true)
    public CartView view(String token) {
        return findCart(token).map(this::toView).orElse(CartView.empty());
    }

    @Transactional(readOnly = true)
    public int itemCount(String token) {
        return findCart(token).map(Cart::totalQuantity).orElse(0);
    }

    @Transactional
    public CartResult addItem(String token, String sku, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        Variant variant = variants.findBySku(sku).orElseThrow(() -> new UnknownVariantException(sku));
        requireOrderable(variant, token);

        Cart cart = findCart(token).orElseGet(() -> carts.save(Cart.create(newToken())));
        cart.itemForVariant(variant.getId())
                .ifPresentOrElse(
                        item -> item.increaseQuantity(quantity),
                        () -> cart.addItem(CartItem.create(cart, variant, quantity)));
        carts.save(cart);
        return new CartResult(cart.getToken(), toView(cart));
    }

    @Transactional
    public CartView changeQuantity(String token, long itemId, int quantity) {
        Cart cart = requireCart(token);
        CartItem item = requireItem(cart, itemId);
        if (quantity <= 0) {
            cart.removeItem(item);
        } else {
            item.changeQuantity(quantity);
        }
        return toView(cart);
    }

    @Transactional
    public CartView removeItem(String token, long itemId) {
        Cart cart = requireCart(token);
        cart.removeItem(requireItem(cart, itemId));
        return toView(cart);
    }

    /**
     * Login-time merge (T8/T13): folds the guest cart (cookie token) into the
     * customer's own cart and returns the surviving cart token to re-set as the
     * cookie. Guest items win on conflict by adding their quantity.
     */
    @Transactional
    public String mergeOnLogin(String guestToken, long customerId) {
        Cart guest = findCart(guestToken).orElse(null);
        Cart account = carts.findByUserId(customerId).orElse(null);
        if (account == null) {
            if (guest == null) {
                return guestToken;
            }
            guest.assignTo(customerId);
            return guest.getToken();
        }
        if (guest != null && !guest.getId().equals(account.getId())) {
            for (CartItem item : guest.getItems()) {
                account.itemForVariant(item.getVariant().getId()).ifPresentOrElse(
                        existing -> existing.increaseQuantity(item.getQuantity()),
                        () -> account.addItem(CartItem.create(account, item.getVariant(), item.getQuantity())));
            }
            carts.delete(guest);
        }
        return account.getToken();
    }

    // ---- internals ----

    private Optional<Cart> findCart(String token) {
        return token == null || token.isBlank() ? Optional.empty() : carts.findByToken(token);
    }

    private Cart requireCart(String token) {
        return findCart(token).orElseThrow(() -> new UnknownVariantException("cart " + token));
    }

    private CartItem requireItem(Cart cart, long itemId) {
        return cart.getItems().stream()
                .filter(i -> i.getId() == itemId)
                .findFirst()
                .orElseThrow(() -> new UnknownVariantException("item " + itemId));
    }

    private void requireOrderable(Variant variant, String ownCartToken) {
        if (!availabilityService.isOrderable(variant, ownCartToken)) {
            throw new NotOrderableException(variant.getSku());
        }
    }

    private CartView toView(Cart cart) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long total = 0;
        List<CartItemView> items = new java.util.ArrayList<>();
        for (CartItem item : cart.getItems()) {
            Variant variant = item.getVariant();
            EffectivePrice price = priceCalculator.effective(variant.getRegularPriceHuf(),
                    variant.getSalePriceHuf(), variant.getSaleFrom(), variant.getSaleTo(), now);
            long unit = price == null ? 0 : price.price().amount();
            long line = unit * item.getQuantity();
            total += line;
            String imageUrl = productImages.findFirstByProductOrderByPositionAsc(variant.getProduct())
                    .map(i -> properties.imageUrl(i.getStorageKey()))
                    .orElse(null);
            items.add(new CartItemView(
                    item.getId(),
                    variant.getProduct().getName(),
                    variantLabel(variant),
                    variant.getSku(),
                    item.getQuantity(),
                    price == null ? null : price.price().formatted(),
                    hu.deposoft.webshop.domain.catalog.Money.huf(line).formatted(),
                    "/product/" + variant.getProduct().getSlug() + "/",
                    imageUrl));
        }
        return new CartView(items, cart.totalQuantity(),
                hu.deposoft.webshop.domain.catalog.Money.huf(total).formatted());
    }

    private String variantLabel(Variant variant) {
        return variant.getAttributeValues().isEmpty() ? null
                : variant.getAttributeValues().stream()
                .map(av -> av.getLabel())
                .reduce((a, b) -> a + " · " + b)
                .orElse(null);
    }

    private static String newToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
