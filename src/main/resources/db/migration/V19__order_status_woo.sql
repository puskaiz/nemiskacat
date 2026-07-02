-- Schema migration: extend orders_status_check to include WooCommerce import-only statuses.
-- PROCESSING/ON_HOLD/FAILED/AWAITING_SHIPMENT are import-only historical states.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('NEW','PAID','PACKING','SHIPPED','COMPLETED','CANCELLED','REFUNDED',
                      'PROCESSING','ON_HOLD','FAILED','AWAITING_SHIPMENT'));
