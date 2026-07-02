package hu.deposoft.webshop.application.checkout;

/** Everything needed to record an order; clientKey makes the call idempotent. */
public record PlaceOrderCommand(
        String clientKey,
        String customerName,
        String email,
        String phone,
        String postcode,
        String city,
        String addressLine,
        String note,
        String shippingMethodCode) {
}
