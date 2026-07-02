package hu.deposoft.webshop.domain.catalog;

/**
 * Single source of truth for the HUF VAT rate rule and net↔gross conversion (CLAUDE.md #6).
 * Catalog prices are stored GROSS; net is derived for display only. Pure & stateless.
 */
public final class VatPricing {

    public static final int STANDARD_RATE = 27;
    public static final int REDUCED_RATE = 5;

    private VatPricing() {}

    /** Tax-class default: "reduced-rate" → 5, everything else (incl. null/blank) → 27. */
    public static int rateForTaxClass(String taxClass) {
        return "reduced-rate".equals(taxClass) ? REDUCED_RATE : STANDARD_RATE;
    }

    /** Explicit product rate if set, else the tax-class default. */
    public static int effectiveRatePercent(Integer vatRatePercent, String taxClass) {
        return vatRatePercent != null ? vatRatePercent : rateForTaxClass(taxClass);
    }

    public static long toGross(long net, int ratePercent) {
        return Math.round(net * (100 + ratePercent) / 100.0);
    }

    public static long toNet(long gross, int ratePercent) {
        return Math.round(gross * 100.0 / (100 + ratePercent));
    }
}
