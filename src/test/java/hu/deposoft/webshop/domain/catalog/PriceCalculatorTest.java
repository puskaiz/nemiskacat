package hu.deposoft.webshop.domain.catalog;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Effective price rule: the sale price applies iff "now" falls inside the
 * optional [saleFrom, saleTo] window (null = open end). Single place where
 * "what does this variant cost right now" is decided.
 */
class PriceCalculatorTest {

    private static final OffsetDateTime NOW =
            OffsetDateTime.of(2026, 6, 13, 12, 0, 0, 0, ZoneOffset.UTC);

    private final PriceCalculator calculator = new PriceCalculator();

    @Test
    void noSalePriceMeansRegularPrice() {
        EffectivePrice p = calculator.effective(3700L, null, null, null, NOW);

        assertThat(p.price()).isEqualTo(Money.huf(3700));
        assertThat(p.onSale()).isFalse();
    }

    @Test
    void saleWithoutWindowIsAlwaysActive() {
        EffectivePrice p = calculator.effective(3700L, 2900L, null, null, NOW);

        assertThat(p.price()).isEqualTo(Money.huf(2900));
        assertThat(p.onSale()).isTrue();
        assertThat(p.regular()).isEqualTo(Money.huf(3700));
    }

    @Test
    void saleInsideWindowIsActive() {
        EffectivePrice p = calculator.effective(3700L, 2900L,
                NOW.minusDays(1), NOW.plusDays(1), NOW);

        assertThat(p.price()).isEqualTo(Money.huf(2900));
        assertThat(p.onSale()).isTrue();
    }

    @Test
    void saleBeforeWindowStartsIsInactive() {
        EffectivePrice p = calculator.effective(3700L, 2900L,
                NOW.plusDays(1), NOW.plusDays(10), NOW);

        assertThat(p.price()).isEqualTo(Money.huf(3700));
        assertThat(p.onSale()).isFalse();
    }

    @Test
    void saleAfterWindowEndsIsInactive() {
        EffectivePrice p = calculator.effective(3700L, 2900L,
                NOW.minusDays(10), NOW.minusDays(1), NOW);

        assertThat(p.price()).isEqualTo(Money.huf(3700));
        assertThat(p.onSale()).isFalse();
    }

    @Test
    void openEndedWindowsWork() {
        assertThat(calculator.effective(3700L, 2900L, NOW.minusDays(1), null, NOW).onSale()).isTrue();
        assertThat(calculator.effective(3700L, 2900L, null, NOW.plusDays(1), NOW).onSale()).isTrue();
    }

    @Test
    void unpricedVariantYieldsNull() {
        assertThat(calculator.effective(null, null, null, null, NOW)).isNull();
    }
}
