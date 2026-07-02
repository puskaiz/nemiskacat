package hu.deposoft.webshop.domain.checkout;

/** One row of the order's VAT breakdown. */
public record VatLine(int ratePercent, long netHuf, long vatHuf, long grossHuf) {
}
