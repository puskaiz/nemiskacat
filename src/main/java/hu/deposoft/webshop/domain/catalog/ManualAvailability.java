package hu.deposoft.webshop.domain.catalog;

/**
 * Manual availability override set by an operator, independent of the stock count.
 * Absence (null) means no override.
 */
public enum ManualAvailability {
    /** Manually flagged as temporarily not orderable (TERV §3.7). */
    TEMPORARILY_UNAVAILABLE,
    /** Orderable ahead of stock arrival. */
    PREORDER
}
