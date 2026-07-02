-- WooCommerce lets customers log in with username OR email. We migrate the WP
-- user_login so the same works here. Self-registered accounts have no username
-- (email-only login).

ALTER TABLE customer ADD COLUMN username TEXT;

CREATE UNIQUE INDEX ux_customer_username_lower ON customer (lower(username)) WHERE username IS NOT NULL;
