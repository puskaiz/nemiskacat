-- The domain must not know WooCommerce: the woo_* identifier columns become
-- source-neutral external ids. They identify the entity in whatever external
-- system the catalog was imported from; the Woo-specific knowledge lives in the
-- integrations layer only.

ALTER TABLE category  RENAME COLUMN woo_term_id      TO external_id;
ALTER TABLE attribute RENAME COLUMN woo_attribute_id TO external_id;
ALTER TABLE product   RENAME COLUMN woo_id           TO external_id;
ALTER TABLE variant   RENAME COLUMN woo_id           TO external_id;

ALTER INDEX ux_variant_woo_id RENAME TO ux_variant_external_id;
