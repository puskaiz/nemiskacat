package hu.deposoft.webshop.domain.checkout;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** VAT computed from gross (prices are gross): net = round(gross/(1+r)), vat = gross - net. */
class VatCalculatorTest {

    private final VatCalculator calculator = new VatCalculator();

    @Test
    void taxClassMapping() {
        assertThat(calculator.ratePercentFor(null)).isEqualTo(27);
        assertThat(calculator.ratePercentFor("")).isEqualTo(27);
        assertThat(calculator.ratePercentFor("reduced-rate")).isEqualTo(5);
    }

    @Test
    void breakdownFromGross27() {
        // 12 700 gross at 27% -> net 10 000, vat 2 700
        List<VatLine> lines = calculator.breakdown(List.of(new GrossAtRate(12_700L, 27)));

        assertThat(lines).containsExactly(new VatLine(27, 10_000L, 2_700L, 12_700L));
    }

    @Test
    void breakdownGroupsByRateAndRounds() {
        List<VatLine> lines = calculator.breakdown(List.of(
                new GrossAtRate(3_700L, 27),
                new GrossAtRate(850L, 27),
                new GrossAtRate(8_200L, 5)));

        // 27%: gross 4550 -> net round(4550/1.27)=3583, vat 967
        // 5%:  gross 8200 -> net round(8200/1.05)=7810, vat 390
        assertThat(lines).containsExactly(
                new VatLine(5, 7_810L, 390L, 8_200L),
                new VatLine(27, 3_583L, 967L, 4_550L));
    }
}
