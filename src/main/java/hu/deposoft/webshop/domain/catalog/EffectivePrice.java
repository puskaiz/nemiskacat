package hu.deposoft.webshop.domain.catalog;

/** What a variant costs right now: the applicable price, the list price, and whether a sale is active. */
public record EffectivePrice(Money price, Money regular, boolean onSale) {
}
