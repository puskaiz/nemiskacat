-- Foundations for workshops + mixed-cart invoicing (T24, phase 1).
-- Each product declares who invoices it and how it is fulfilled; an optional
-- explicit VAT rate overrides the tax-class default (workshops = 27%, settable).
-- Existing (WooCommerce) products keep KULCS_SOFT / SHIP.

ALTER TABLE product ADD COLUMN invoice_source TEXT NOT NULL DEFAULT 'KULCS_SOFT'
    CHECK (invoice_source IN ('KULCS_SOFT', 'BILLINGO'));
ALTER TABLE product ADD COLUMN fulfilment_type TEXT NOT NULL DEFAULT 'SHIP'
    CHECK (fulfilment_type IN ('SHIP', 'EVENT'));
ALTER TABLE product ADD COLUMN vat_rate_percent INT;

-- the invoice source is snapshotted on the order line at placement
ALTER TABLE order_item ADD COLUMN invoice_source TEXT NOT NULL DEFAULT 'KULCS_SOFT'
    CHECK (invoice_source IN ('KULCS_SOFT', 'BILLINGO'));
