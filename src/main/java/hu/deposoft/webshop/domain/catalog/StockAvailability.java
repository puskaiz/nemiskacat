package hu.deposoft.webshop.domain.catalog;

/**
 * Result of evaluating stock: the derived status plus the "last few pieces" flag
 * (only meaningful when {@link StockStatus#IN_STOCK}).
 */
public record StockAvailability(StockStatus status, boolean lowStock) {
}
