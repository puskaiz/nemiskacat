package hu.deposoft.webshop.domain.catalog;

import org.junit.jupiter.api.Test;

import static hu.deposoft.webshop.domain.catalog.ManualAvailability.PREORDER;
import static hu.deposoft.webshop.domain.catalog.ManualAvailability.TEMPORARILY_UNAVAILABLE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The derived stock-status rules (CLAUDE.md #5, TERV §3.7). The website never sees
 * raw quantities — only a status plus a "last few" low-stock flag. This calculator
 * is the single place the status is decided.
 */
class StockStatusCalculatorTest {

    private final StockStatusCalculator calculator = new StockStatusCalculator();

    // ---- precedence: lifecycle wins over everything ----

    @Test
    void discontinuedWinsEvenWithStockAndPreorder() {
        StockAvailability result = calculator.evaluate(
                true, PREORDER, true, 100, 5);

        assertThat(result.status()).isEqualTo(StockStatus.DISCONTINUED);
        assertThat(result.lowStock()).isFalse();
    }

    // ---- manual flags ----

    @Test
    void temporarilyUnavailableWinsOverStock() {
        StockAvailability result = calculator.evaluate(
                false, TEMPORARILY_UNAVAILABLE, true, 100, 5);

        assertThat(result.status()).isEqualTo(StockStatus.TEMPORARILY_UNAVAILABLE);
    }

    @Test
    void preorderIsSellableEvenAtZeroQuantity() {
        StockAvailability result = calculator.evaluate(
                false, PREORDER, true, 0, 5);

        assertThat(result.status()).isEqualTo(StockStatus.PREORDER);
    }

    // ---- not stock-managed ----

    @Test
    void unmanagedStockIsAlwaysInStockWithoutLowFlag() {
        StockAvailability result = calculator.evaluate(
                false, null, false, 0, 5);

        assertThat(result.status()).isEqualTo(StockStatus.IN_STOCK);
        assertThat(result.lowStock()).isFalse();
    }

    // ---- managed stock ----

    @Test
    void managedStockAtOrBelowZeroIsOutOfStock() {
        assertThat(calculator.evaluate(false, null, true, 0, 5).status())
                .isEqualTo(StockStatus.OUT_OF_STOCK);
        assertThat(calculator.evaluate(false, null, true, -3, 5).status())
                .isEqualTo(StockStatus.OUT_OF_STOCK);
    }

    @Test
    void managedStockAboveThresholdIsInStockWithoutLowFlag() {
        StockAvailability result = calculator.evaluate(
                false, null, true, 6, 5);

        assertThat(result.status()).isEqualTo(StockStatus.IN_STOCK);
        assertThat(result.lowStock()).isFalse();
    }

    @Test
    void managedStockAtThresholdIsInStockButLow() {
        StockAvailability result = calculator.evaluate(
                false, null, true, 5, 5);

        assertThat(result.status()).isEqualTo(StockStatus.IN_STOCK);
        assertThat(result.lowStock()).isTrue();
    }

    @Test
    void managedStockJustAboveThresholdIsNotLow() {
        StockAvailability result = calculator.evaluate(
                false, null, true, 6, 5);

        assertThat(result.lowStock()).isFalse();
    }

    @Test
    void lowStockFlagIsNeverSetWhenOutOfStock() {
        StockAvailability result = calculator.evaluate(
                false, null, true, 0, 5);

        assertThat(result.lowStock()).isFalse();
    }
}
