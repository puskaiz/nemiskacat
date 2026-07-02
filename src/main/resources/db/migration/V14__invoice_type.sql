-- A credit note (storno) is a second document per (order, source), so widen the
-- uniqueness to include the document type. Default existing rows to INVOICE.
ALTER TABLE invoice ADD COLUMN type TEXT NOT NULL DEFAULT 'INVOICE'
    CHECK (type IN ('INVOICE', 'CREDIT_NOTE'));

ALTER TABLE invoice DROP CONSTRAINT invoice_order_id_source_key;
ALTER TABLE invoice ADD CONSTRAINT invoice_order_source_type_key UNIQUE (order_id, source, type);
