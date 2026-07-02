-- HUMAN-RUN ONLY. Wipes test data before the real cutover import.
-- Run against the TARGET app DB (never automated; never against production
-- without sign-off). Order of truncation respects FKs via CASCADE.
-- Cutover sequence: this script -> import catalog -> import customers -> import orders.
BEGIN;
-- `invoice` is included for a clean slate even though no invoices are imported
-- (see ADR-0007: invoices are generated post-cutover, not migrated from Woo).
TRUNCATE TABLE payment, invoice, order_item, orders, reservation, customer
    RESTART IDENTITY CASCADE;
COMMIT;
