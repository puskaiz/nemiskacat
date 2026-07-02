-- Bring over WordPress roles. WP has exactly three single roles here:
-- customer (4640), subscriber (535), administrator (14). Stored as the granted
-- Spring Security role; admins become staff accounts for the future admin SPA.

ALTER TABLE customer ADD COLUMN role TEXT NOT NULL DEFAULT 'CUSTOMER'
    CHECK (role IN ('CUSTOMER', 'SUBSCRIBER', 'ADMIN'));

CREATE INDEX ix_customer_role ON customer (role);
