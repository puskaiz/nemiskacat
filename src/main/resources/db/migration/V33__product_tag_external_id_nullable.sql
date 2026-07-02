-- Allow admin-created product tags (no Woo term id). Postgres UNIQUE permits multiple NULLs.
ALTER TABLE product_tag ALTER COLUMN external_id DROP NOT NULL;
