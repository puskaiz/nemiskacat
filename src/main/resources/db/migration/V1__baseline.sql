-- Baseline migration for the nemiskacat.hu webshop.
-- Establishes Flyway history and the extensions/utility tables the schema relies on.
-- Domain tables (products, variants, categories, orders, ...) arrive in later tasks.

-- Trigram search support, used later for fuzzy SKU/name matching during the
-- Kulcs-Soft <-> WooCommerce reconciliation (T2) and admin search.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Small key/value table for application-managed metadata (e.g. last sync markers).
CREATE TABLE app_metadata (
    key         TEXT PRIMARY KEY,
    value       TEXT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE app_metadata IS 'Application-managed key/value metadata. Timestamps stored in UTC (TIMESTAMPTZ).';
