/**
 * Domain layer: entities and business rules (stock-status derivation, coupons,
 * pricing). Framework-light and depended upon by all other layers; it depends on
 * none of them. Business logic that the web/api adapters need lives behind the
 * {@code application} service layer, not here directly.
 */
package hu.deposoft.webshop.domain;
