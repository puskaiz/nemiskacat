-- Imported historical orders may reference products no longer in the catalog.
-- The line keeps its snapshot (product_name, sku, prices); the live FK is optional.
ALTER TABLE order_item ALTER COLUMN variant_id DROP NOT NULL;
