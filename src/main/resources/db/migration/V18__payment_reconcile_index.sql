-- H3 refund reconciliation sweep: candidate lookup filters payments by state + last check time
-- (findReconcilable). Without this the hourly sweep seq-scans payment as it grows.
CREATE INDEX ix_payment_reconcile ON payment (state, last_checked_at);
