package hu.deposoft.webshop.domain.checkout;

/** A gross amount carrying its VAT rate — input of the VAT breakdown. */
public record GrossAtRate(long grossHuf, int ratePercent) {
}
