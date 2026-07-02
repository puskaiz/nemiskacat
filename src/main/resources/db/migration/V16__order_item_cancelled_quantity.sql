-- 2b-2: per-line cancellation. A cancelled booking frees its seats by being
-- subtracted from the availability sum, while the original quantity (history) stays.
ALTER TABLE order_item ADD COLUMN cancelled_quantity INT NOT NULL DEFAULT 0
    CHECK (cancelled_quantity >= 0 AND cancelled_quantity <= quantity);
