package hu.deposoft.webshop.integrations.woo;

/**
 * Port for loading a catalog snapshot. Implemented by the JSON-export reader now;
 * the WooCommerce REST source plugs in behind the same port later (T4 design).
 */
public interface CatalogSource {

    SourceCatalog load();
}
