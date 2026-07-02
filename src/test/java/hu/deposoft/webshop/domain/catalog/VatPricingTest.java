package hu.deposoft.webshop.domain.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class VatPricingTest {
    @Test void toGrossStandard()        { assertThat(VatPricing.toGross(1000, 27)).isEqualTo(1270); }
    @Test void toGrossReduced()         { assertThat(VatPricing.toGross(1000, 5)).isEqualTo(1050); }
    @Test void toNetExact()             { assertThat(VatPricing.toNet(1270, 27)).isEqualTo(1000); }
    @Test void toNetRounds()            { assertThat(VatPricing.toNet(1000, 27)).isEqualTo(787); }
    @Test void rateForTaxClassReduced() { assertThat(VatPricing.rateForTaxClass("reduced-rate")).isEqualTo(5); }
    @Test void rateForTaxClassDefault() {
        assertThat(VatPricing.rateForTaxClass(null)).isEqualTo(27);
        assertThat(VatPricing.rateForTaxClass("standard")).isEqualTo(27);
    }
    @Test void effectiveExplicitWins()  { assertThat(VatPricing.effectiveRatePercent(18, "reduced-rate")).isEqualTo(18); }
    @Test void effectiveFallsBack() {
        assertThat(VatPricing.effectiveRatePercent(null, "reduced-rate")).isEqualTo(5);
        assertThat(VatPricing.effectiveRatePercent(null, null)).isEqualTo(27);
    }
}
