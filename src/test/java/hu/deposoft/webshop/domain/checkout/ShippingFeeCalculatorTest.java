package hu.deposoft.webshop.domain.checkout;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The business-confirmed fee table (docs/poc/2026-06-13-shipping-vat-config.md):
 * GLS 850/1500/1700 Ft by weight band, free at gross cart >= 39 000 Ft,
 * Posta flat 7 900, pickup 0. Band edges inclusive. GLS unavailable over 500 kg.
 */
class ShippingFeeCalculatorTest {

    private final ShippingFeeCalculator calculator = new ShippingFeeCalculator(List.of(
            ShippingMethod.weightBanded("gls", "GLS futárszolgálat", true, List.of(
                    new WeightBand(900, 850L),
                    new WeightBand(5_000, 1_500L),
                    new WeightBand(500_000, 1_700L))),
            ShippingMethod.flat("posta", "Magyar Posta házhozszállítás", 7_900L),
            ShippingMethod.flat("pickup", "Személyes átvétel üzletünkben", 0L)),
            39_000L);

    private long feeOf(String code, long cartGross, int weightGrams) {
        Optional<ShippingOption> option = calculator.options(cartGross, weightGrams).stream()
                .filter(o -> o.code().equals(code)).findFirst();
        return option.orElseThrow().grossHuf();
    }

    @Test
    void glsBandsWithInclusiveEdges() {
        assertThat(feeOf("gls", 10_000, 200)).isEqualTo(850);
        assertThat(feeOf("gls", 10_000, 900)).isEqualTo(850);     // edge: still first band
        assertThat(feeOf("gls", 10_000, 901)).isEqualTo(1_500);
        assertThat(feeOf("gls", 10_000, 5_000)).isEqualTo(1_500); // edge
        assertThat(feeOf("gls", 10_000, 5_001)).isEqualTo(1_700);
        assertThat(feeOf("gls", 10_000, 500_000)).isEqualTo(1_700);
    }

    @Test
    void glsIsFreeAtThreshold() {
        assertThat(feeOf("gls", 39_000, 5_000)).isZero();
        assertThat(feeOf("gls", 38_999, 5_000)).isEqualTo(1_500);
    }

    @Test
    void glsUnavailableOverMaxWeight() {
        List<ShippingOption> options = calculator.options(10_000, 500_001);

        assertThat(options).extracting(ShippingOption::code).doesNotContain("gls");
    }

    @Test
    void flatMethodsIgnoreWeightAndThreshold() {
        assertThat(feeOf("posta", 100_000, 700_000)).isEqualTo(7_900);
        assertThat(feeOf("pickup", 1_000, 0)).isZero();
    }

    @Test
    void zeroWeightFallsIntoFirstBand() {
        assertThat(feeOf("gls", 10_000, 0)).isEqualTo(850);
    }
}
