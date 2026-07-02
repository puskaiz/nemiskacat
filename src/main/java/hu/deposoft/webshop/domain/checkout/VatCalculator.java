package hu.deposoft.webshop.domain.checkout;

import hu.deposoft.webshop.domain.catalog.VatPricing;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * VAT arithmetic from gross amounts (catalog prices are gross, CLAUDE.md #6):
 * net = round(gross / (1 + rate)), vat = gross - net, grouped by rate.
 * Tax-class mapping follows the live config: '' = 27%, reduced-rate = 5%.
 */
public class VatCalculator {

    public static final int STANDARD_RATE = VatPricing.STANDARD_RATE;
    public static final int REDUCED_RATE = VatPricing.REDUCED_RATE;

    public int ratePercentFor(String taxClass) {
        return VatPricing.rateForTaxClass(taxClass);
    }

    public List<VatLine> breakdown(List<GrossAtRate> amounts) {
        Map<Integer, Long> grossByRate = new TreeMap<>();
        for (GrossAtRate a : amounts) {
            grossByRate.merge(a.ratePercent(), a.grossHuf(), Long::sum);
        }
        return grossByRate.entrySet().stream()
                .map(e -> {
                    long gross = e.getValue();
                    long net = Math.round(gross * 100.0 / (100 + e.getKey()));
                    return new VatLine(e.getKey(), net, gross - net, gross);
                })
                .toList();
    }
}
