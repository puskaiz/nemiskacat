package hu.deposoft.webshop.domain.catalog;

import java.time.OffsetDateTime;

/**
 * Decides the effective price of a variant: the sale price applies iff "now"
 * falls inside the optional [saleFrom, saleTo] window (null = open end).
 * Pure and stateless — the single source of the pricing-now rule.
 */
public class PriceCalculator {

    /** @return the effective price, or null if the variant has no regular price. */
    public EffectivePrice effective(Long regularHuf, Long saleHuf,
                                    OffsetDateTime saleFrom, OffsetDateTime saleTo,
                                    OffsetDateTime now) {
        if (regularHuf == null) {
            return null;
        }
        Money regular = Money.huf(regularHuf);
        boolean saleActive = saleHuf != null
                && (saleFrom == null || !now.isBefore(saleFrom))
                && (saleTo == null || !now.isAfter(saleTo));
        return saleActive
                ? new EffectivePrice(Money.huf(saleHuf), regular, true)
                : new EffectivePrice(regular, regular, false);
    }
}
