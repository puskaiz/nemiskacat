package hu.deposoft.webshop.domain.catalog;

/** Product kind. Both use the uniform variant model (simple = one default variant). */
public enum ProductType {
    SIMPLE,
    VARIABLE,
    /** A workshop: each variant is a session (date + capacity). */
    WORKSHOP
}
