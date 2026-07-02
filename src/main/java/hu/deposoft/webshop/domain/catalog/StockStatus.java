package hu.deposoft.webshop.domain.catalog;

/**
 * The derived stock status shown to the website. Raw quantities never leave the
 * service layer (CLAUDE.md #5).
 */
public enum StockStatus {
    IN_STOCK,
    OUT_OF_STOCK,
    TEMPORARILY_UNAVAILABLE,
    DISCONTINUED,
    PREORDER
}
