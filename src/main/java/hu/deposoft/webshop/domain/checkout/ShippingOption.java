package hu.deposoft.webshop.domain.checkout;

/** A shipping method offered for a concrete cart, with its computed fee. */
public record ShippingOption(String code, String name, long grossHuf, boolean free) {
}
