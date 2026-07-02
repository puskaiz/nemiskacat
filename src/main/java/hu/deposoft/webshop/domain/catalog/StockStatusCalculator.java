package hu.deposoft.webshop.domain.catalog;

/**
 * Computes the derived {@link StockAvailability} from the inputs that the rest of
 * the system already knows (CLAUDE.md #5, TERV §3.7). Pure and stateless: the
 * single source of truth for how stock turns into a website-visible status.
 *
 * <p>{@code availableQty} is the availability ledger
 * ({@code last sync qty − web orders since − POS sales since}); it is passed in so
 * this calculator stays free of persistence and fully unit-testable.
 */
public class StockStatusCalculator {

    /**
     * @param discontinued      product lifecycle flag (kifutott) — overrides all else
     * @param manualAvailability operator override, or {@code null} for none
     * @param manageStock       whether stock is tracked for this variant
     * @param availableQty      available quantity from the ledger
     * @param lowStockThreshold at or below this (and in stock) the "last few" flag is set
     */
    public StockAvailability evaluate(
            boolean discontinued,
            ManualAvailability manualAvailability,
            boolean manageStock,
            int availableQty,
            int lowStockThreshold) {

        if (discontinued) {
            return new StockAvailability(StockStatus.DISCONTINUED, false);
        }
        if (manualAvailability == ManualAvailability.TEMPORARILY_UNAVAILABLE) {
            return new StockAvailability(StockStatus.TEMPORARILY_UNAVAILABLE, false);
        }
        if (manualAvailability == ManualAvailability.PREORDER) {
            return new StockAvailability(StockStatus.PREORDER, false);
        }
        if (!manageStock) {
            return new StockAvailability(StockStatus.IN_STOCK, false);
        }
        if (availableQty <= 0) {
            return new StockAvailability(StockStatus.OUT_OF_STOCK, false);
        }
        return new StockAvailability(StockStatus.IN_STOCK, availableQty <= lowStockThreshold);
    }
}
