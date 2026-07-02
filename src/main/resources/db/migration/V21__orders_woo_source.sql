-- Provenance for imported WooCommerce orders. Native orders leave these null.
-- woo_order_id is the idempotency key for re-running the order import.
ALTER TABLE orders ADD COLUMN woo_order_id BIGINT;
ALTER TABLE orders ADD COLUMN source TEXT;
ALTER TABLE orders ADD CONSTRAINT orders_woo_order_id_key UNIQUE (woo_order_id);
