-- 2b-2: a line-scoped credit note links to the cancelled order_item. The 2b-1
-- one-row-per-(order,source,type) rule only applies to whole-order documents
-- (order_item_id NULL); line-scoped credit notes are unique per (order_item, type).
-- cascade with the order aggregate: order_item already cascades from orders,
-- so a line-scoped credit note is removed if its line is ever deleted.
ALTER TABLE invoice ADD COLUMN order_item_id BIGINT REFERENCES order_item (id) ON DELETE CASCADE;

ALTER TABLE invoice DROP CONSTRAINT invoice_order_source_type_key;

CREATE UNIQUE INDEX ux_invoice_order_source_type
    ON invoice (order_id, source, type) WHERE order_item_id IS NULL;

CREATE UNIQUE INDEX ux_invoice_order_item_type
    ON invoice (order_item_id, type) WHERE order_item_id IS NOT NULL;
