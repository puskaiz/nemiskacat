package hu.deposoft.webshop.application.checkout;

import hu.deposoft.webshop.application.cart.CartService;
import hu.deposoft.webshop.application.cart.CartService.CartView;
import hu.deposoft.webshop.application.catalog.AvailabilityService;
import hu.deposoft.webshop.config.ShippingProperties;
import hu.deposoft.webshop.domain.cart.Cart;
import hu.deposoft.webshop.domain.cart.CartItem;
import hu.deposoft.webshop.domain.cart.CartRepository;
import hu.deposoft.webshop.domain.catalog.AttributeValue;
import hu.deposoft.webshop.domain.catalog.EffectivePrice;
import hu.deposoft.webshop.domain.catalog.PriceCalculator;
import hu.deposoft.webshop.domain.catalog.Variant;
import hu.deposoft.webshop.domain.catalog.VariantRepository;
import hu.deposoft.webshop.domain.checkout.GrossAtRate;
import hu.deposoft.webshop.domain.checkout.ShippingFeeCalculator;
import hu.deposoft.webshop.domain.checkout.ShippingOption;
import hu.deposoft.webshop.domain.checkout.VatCalculator;
import hu.deposoft.webshop.domain.checkout.VatLine;
import hu.deposoft.webshop.domain.order.Order;
import hu.deposoft.webshop.domain.order.OrderItem;
import hu.deposoft.webshop.domain.order.OrderRepository;
import hu.deposoft.webshop.domain.order.Reservation;
import hu.deposoft.webshop.domain.order.ReservationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Checkout (T9): start = stock re-check + 15-minute hold for flagged products
 * (overselling protection point 2 of 3); placeOrder = pessimistic variant locks +
 * final ledger check + price-locked order recording (point 3), idempotent via the
 * client key. Payment (KHPos) arrives in T10 — orders end in NEW.
 */
@Service
@Slf4j
public class CheckoutService {

    static final Duration RESERVATION_TTL = Duration.ofMinutes(15);

    private final CartRepository carts;
    private final VariantRepository variants;
    private final OrderRepository orders;
    private final ReservationRepository reservations;
    private final CartService cartService;
    private final AvailabilityService availability;
    private final ShippingFeeCalculator shippingCalculator;

    private final PriceCalculator priceCalculator = new PriceCalculator();
    private final VatCalculator vatCalculator = new VatCalculator();

    public CheckoutService(CartRepository carts, VariantRepository variants, OrderRepository orders,
                           ReservationRepository reservations, CartService cartService,
                           AvailabilityService availability, ShippingProperties shippingProperties) {
        this.carts = carts;
        this.variants = variants;
        this.orders = orders;
        this.reservations = reservations;
        this.cartService = cartService;
        this.availability = availability;
        this.shippingCalculator = shippingProperties.calculator();
    }

    // ---- views / errors ----

    public record CheckoutView(CartView cart, int totalWeightGrams, long itemsGrossHuf,
                               List<ShippingOption> shippingOptions) {
    }

    public static class EmptyCartException extends RuntimeException {
    }

    /** One unfulfillable cart line: what it is, how many were wanted, how many remain. */
    public record UnavailableItem(String sku, String name, int available, int requested) {
    }

    public static class UnavailableItemsException extends RuntimeException {
        private final List<UnavailableItem> items;

        public UnavailableItemsException(List<UnavailableItem> items) {
            super("Items no longer available: "
                    + items.stream().map(UnavailableItem::sku).reduce((a, b) -> a + ", " + b).orElse(""));
            this.items = List.copyOf(items);
        }

        /** The unfulfillable lines, each with the remaining available quantity. */
        public List<UnavailableItem> items() {
            return items;
        }
    }

    public static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(String sku) {
            super("Insufficient stock for '%s'".formatted(sku));
        }
    }

    public static class InvalidShippingMethodException extends RuntimeException {
        public InvalidShippingMethodException(String code) {
            super("Shipping method '%s' is not available for this cart".formatted(code));
        }
    }

    // ---- operations ----

    /**
     * Begin checkout: re-validate every item (point 2 of 3), hold flagged products
     * for 15 minutes, and return the shipping options with computed fees.
     */
    @Transactional
    public CheckoutView start(String cartToken) {
        Cart cart = requireNonEmptyCart(cartToken);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        List<UnavailableItem> unavailable = new ArrayList<>();
        for (CartItem item : cart.getItems()) {
            if (!availability.canFulfil(item.getVariant(), cartToken, item.getQuantity())) {
                int available = Math.max(0, availability.availableQty(item.getVariant(), cartToken));
                unavailable.add(new UnavailableItem(item.getVariant().getSku(),
                        displayName(item.getVariant()), available, item.getQuantity()));
            }
        }
        if (!unavailable.isEmpty()) {
            throw new UnavailableItemsException(unavailable);
        }

        for (CartItem item : cart.getItems()) {
            if (item.getVariant().getProduct().isReserveOnCheckout()) {
                holdVariant(item.getVariant(), cartToken, item.getQuantity(), now);
            }
        }
        return view(cart);
    }

    /** Idempotent order recording; replays with the same client key return the existing order. */
    @Transactional
    public Order placeOrder(String cartToken, PlaceOrderCommand command) {
        Optional<Order> existing = orders.findByClientKey(command.clientKey());
        if (existing.isPresent()) {
            log.info("Order replay for client key {} -> order {}", command.clientKey(), existing.get().getId());
            return existing.get();
        }

        Cart cart = requireNonEmptyCart(cartToken);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // lock the variant rows in id order, then re-check the ledger inside the lock
        List<Long> variantIds = cart.getItems().stream()
                .map(i -> i.getVariant().getId())
                .sorted()
                .toList();
        variants.lockAllByIdIn(variantIds);
        for (CartItem item : cart.getItems()) {
            if (!availability.canFulfil(item.getVariant(), cartToken, item.getQuantity())) {
                throw new InsufficientStockException(item.getVariant().getSku());
            }
        }

        long itemsGross = itemsGross(cart, now);
        int weight = totalWeightGrams(cart);
        ShippingOption shipping = shippingCalculator.option(command.shippingMethodCode(), itemsGross, weight)
                .orElseThrow(() -> new InvalidShippingMethodException(command.shippingMethodCode()));

        Order order = Order.place(command.clientKey(), command.customerName(), command.email(),
                command.phone(), command.postcode(), command.city(), command.addressLine(),
                command.note(), shipping.code(), shipping.name(), shipping.grossHuf());
        for (CartItem item : cart.getItems()) {
            Variant variant = item.getVariant();
            EffectivePrice price = priceCalculator.effective(variant.getRegularPriceHuf(),
                    variant.getSalePriceHuf(), variant.getSaleFrom(), variant.getSaleTo(), now);
            long unitGross = price == null ? 0 : price.price().amount();
            order.addItem(OrderItem.create(order, variant,
                    variant.getProduct().getName(), variantLabel(variant), variant.getSku(),
                    unitGross, vatRate(variant.getProduct()),
                    item.getQuantity(), variant.getProduct().getInvoiceSource()));
        }
        Order saved = orders.save(order);

        reservations.deleteByCartToken(cartToken);
        cart.getItems().clear();
        carts.save(cart);

        log.info("Order {} recorded: {} items, total {} HUF, shipping {}",
                saved.getId(), saved.getItems().size(), saved.getTotalGrossHuf(), shipping.code());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Order> findByClientKey(String clientKey) {
        return orders.findWithItemsByClientKey(clientKey);
    }

    public List<VatLine> vatBreakdown(Order order) {
        List<GrossAtRate> amounts = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            amounts.add(new GrossAtRate(item.getLineGrossHuf(), item.getTaxRatePercent()));
        }
        if (order.getShipGrossHuf() > 0) {
            amounts.add(new GrossAtRate(order.getShipGrossHuf(), VatCalculator.STANDARD_RATE));
        }
        return vatCalculator.breakdown(amounts);
    }

    // ---- internals ----

    private CheckoutView view(Cart cart) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long itemsGross = itemsGross(cart, now);
        int weight = totalWeightGrams(cart);
        return new CheckoutView(cartService.view(cart.getToken()), weight, itemsGross,
                shippingCalculator.options(itemsGross, weight));
    }

    private Cart requireNonEmptyCart(String cartToken) {
        Cart cart = (cartToken == null || cartToken.isBlank() ? Optional.<Cart>empty()
                : carts.findByToken(cartToken))
                .orElseThrow(EmptyCartException::new);
        if (cart.getItems().isEmpty()) {
            throw new EmptyCartException();
        }
        return cart;
    }

    private void holdVariant(Variant variant, String cartToken, int quantity, OffsetDateTime now) {
        OffsetDateTime expiresAt = now.plus(RESERVATION_TTL);
        reservations.findByVariantAndCartToken(variant, cartToken)
                .ifPresentOrElse(
                        r -> r.renew(quantity, expiresAt),
                        () -> reservations.save(Reservation.hold(variant, cartToken, quantity, expiresAt)));
    }

    private long itemsGross(Cart cart, OffsetDateTime now) {
        long total = 0;
        for (CartItem item : cart.getItems()) {
            Variant v = item.getVariant();
            EffectivePrice price = priceCalculator.effective(v.getRegularPriceHuf(), v.getSalePriceHuf(),
                    v.getSaleFrom(), v.getSaleTo(), now);
            total += (price == null ? 0 : price.price().amount()) * item.getQuantity();
        }
        return total;
    }

    /** Weight of the shippable (SHIP) lines only — workshop seats (EVENT) don't ship. */
    private int totalWeightGrams(Cart cart) {
        int total = 0;
        for (CartItem item : cart.getItems()) {
            if (item.getVariant().getProduct().getFulfilmentType()
                    != hu.deposoft.webshop.domain.catalog.FulfilmentType.SHIP) {
                continue;
            }
            Integer weight = item.getVariant().getWeightGrams();
            total += (weight == null ? 0 : weight) * item.getQuantity();
        }
        return total;
    }

    /** VAT rate for a product: its explicit rate wins, else the tax-class default. */
    private int vatRate(hu.deposoft.webshop.domain.catalog.Product product) {
        return product.getVatRatePercent() != null
                ? product.getVatRatePercent()
                : vatCalculator.ratePercentFor(product.getTaxClass());
    }

    private String variantLabel(Variant variant) {
        return variant.getAttributeValues().isEmpty() ? null
                : variant.getAttributeValues().stream()
                .map(AttributeValue::getLabel)
                .reduce((a, b) -> a + " · " + b)
                .orElse(null);
    }

    /** Customer-facing name for a variant: product name plus its variant label, if any. */
    private String displayName(Variant variant) {
        String name = variant.getProduct().getName();
        String label = variantLabel(variant);
        return (label == null || label.isBlank()) ? name : name + " – " + label;
    }
}
