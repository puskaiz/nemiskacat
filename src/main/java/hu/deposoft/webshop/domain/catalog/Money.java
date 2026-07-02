package hu.deposoft.webshop.domain.catalog;

/**
 * A monetary amount in minor units (CLAUDE.md #6). For HUF the minor unit is the
 * forint (scale 0), since fillér is not in circulation. Amounts are non-negative
 * (catalog prices); arithmetic for totals/VAT arrives with checkout (T9).
 */
public record Money(long amount, String currency) {

    public Money {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative: " + amount);
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
    }

    public static Money huf(long amount) {
        return new Money(amount, "HUF");
    }

    /** Deterministic Hungarian display format: NBSP thousands grouping, e.g. "3\u00A0700 Ft". */
    public String formatted() {
        StringBuilder sb = new StringBuilder(String.valueOf(amount));
        for (int i = sb.length() - 3; i > 0; i -= 3) {
            sb.insert(i, '\u00A0');
        }
        return sb + " Ft";
    }
}
