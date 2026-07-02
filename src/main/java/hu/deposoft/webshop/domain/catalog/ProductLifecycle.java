package hu.deposoft.webshop.domain.catalog;

/** Product lifecycle. DISCONTINUED (kifutott) overrides stock for status (TERV §3.7). */
public enum ProductLifecycle {
    ACTIVE,
    DISCONTINUED
}
